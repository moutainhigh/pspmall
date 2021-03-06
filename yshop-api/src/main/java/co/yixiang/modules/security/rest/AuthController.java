package co.yixiang.modules.security.rest;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaUserInfo;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import co.yixiang.annotation.AnonymousAccess;
import co.yixiang.aop.log.Log;
import co.yixiang.common.api.ApiCode;
import co.yixiang.common.api.ApiResult;
import co.yixiang.exception.ErrorRequestException;
import co.yixiang.modules.security.config.SecurityProperties;
import co.yixiang.modules.security.rest.param.RegParam;
import co.yixiang.modules.security.rest.param.VerityParam;
import co.yixiang.modules.security.security.TokenProvider;
import co.yixiang.modules.security.security.vo.AuthUser;
import co.yixiang.modules.security.security.vo.JwtUser;
import co.yixiang.modules.security.service.OnlineUserService;
import co.yixiang.modules.user.entity.YxUser;
import co.yixiang.modules.user.entity.YxWechatUser;
import co.yixiang.modules.user.service.YxUserService;
import co.yixiang.modules.user.service.YxWechatUserService;
import co.yixiang.modules.user.web.vo.YxUserQueryVo;
import co.yixiang.utils.OrderUtil;
import co.yixiang.utils.RedisUtil;
import co.yixiang.utils.RedisUtils;
import co.yixiang.utils.SecurityUtils;
import com.vdurmont.emoji.EmojiParser;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpOAuth2AccessToken;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author hupeng
 * @date 2020/01/12
 */
@Slf4j
@RestController
@Api(tags = "用户授权中心")
public class AuthController {

    @Value("${single.login:false}")
    private Boolean singleLogin;
    private final SecurityProperties properties;
    private final RedisUtils redisUtils;
    private final UserDetailsService userDetailsService;
    private final OnlineUserService onlineUserService;
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final YxUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final WxMpService wxService;
    private final YxWechatUserService wechatUserService;
    private final WxMaService wxMaService;

    public AuthController(SecurityProperties properties, RedisUtils redisUtils,
                          UserDetailsService userDetailsService,
                          OnlineUserService onlineUserService, TokenProvider tokenProvider,
                          AuthenticationManagerBuilder authenticationManagerBuilder,
                          YxUserService userService, PasswordEncoder passwordEncoder,
                          WxMpService wxService, YxWechatUserService wechatUserService,
                          WxMaService wxMaService) {
        this.properties = properties;
        this.redisUtils = redisUtils;
        this.userDetailsService = userDetailsService;
        this.onlineUserService = onlineUserService;
        this.tokenProvider = tokenProvider;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.wxService = wxService;
        this.wechatUserService = wechatUserService;
        this.wxMaService = wxMaService;
    }

    @Log("H5用户登录")
    @ApiOperation("H5登录授权")
    @AnonymousAccess
    @PostMapping(value = "/login")
    public ApiResult<Map<String, String>> login(@Validated @RequestBody AuthUser authUser,
                                                HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(authUser.getUsername(), authUser.getPassword());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 生成令牌
        String token = tokenProvider.createToken(authentication);
        final JwtUser jwtUser = (JwtUser) authentication.getPrincipal();
        // 保存在线信息
        onlineUserService.save(jwtUser, token, request);

        Date expiresTime = tokenProvider.getExpirationDateFromToken(token);
        String expiresTimeStr = DateUtil.formatDateTime(expiresTime);
        // 返回 token 与 用户信息
        Map<String, Object> authInfo = new HashMap<String, Object>(2) {{
            put("token", token);
            put("expires_time", expiresTimeStr);
        }};
        if (singleLogin) {
            //踢掉之前已经登录的token
            onlineUserService.checkLoginOnUser(authUser.getUsername(), token);
        }
        // 返回 token
        return ApiResult.ok(authInfo);
    }

    /**
     * 微信公众号授权
     */
    @AnonymousAccess
    @GetMapping("/wechat/auth")
    @ApiOperation(value = "微信公众号授权", notes = "微信公众号授权")
    public ApiResult<Object> authLogin(@RequestParam(value = "code") String code,
                                       @RequestParam(value = "spread") String spread,
                                       HttpServletRequest request) {

        try {
            WxMpOAuth2AccessToken wxMpOAuth2AccessToken = wxService.oauth2getAccessToken(code);
            WxMpUser wxMpUser = wxService.oauth2getUserInfo(wxMpOAuth2AccessToken, null);
            String openid = wxMpUser.getOpenId();
            YxWechatUser wechatUser = wechatUserService.getUserInfo(openid);


            JwtUser jwtUser = null;
            if (ObjectUtil.isNotNull(wechatUser)) {
                YxUserQueryVo yxUserQueryVo = userService.getYxUserById(wechatUser.getUid());
                if (ObjectUtil.isNotNull(yxUserQueryVo)) {
                    jwtUser = (JwtUser) userDetailsService.loadUserByUsername(wechatUser.getOpenid());
                } else {
                    if (ObjectUtil.isNotNull(wechatUser)) {
                        wechatUserService.removeById(wechatUser.getUid());
                    }
                    if (ObjectUtil.isNotNull(yxUserQueryVo)) {
                        userService.removeById(yxUserQueryVo.getUid());
                    }
                    return ApiResult.fail(ApiCode.FAIL_AUTH, "授权失败");
                }


            } else {

                //过滤掉表情
                String nickname = EmojiParser.removeAllEmojis(wxMpUser.getNickname());
                log.info("昵称：{}", nickname);
                //用户保存
                YxUser user = new YxUser();
                user.setAccount(nickname);
                user.setUsername(wxMpUser.getOpenId());
                user.setPassword(passwordEncoder.encode("123456"));
                user.setPwd(passwordEncoder.encode("123456"));
                user.setPhone("");
                user.setUserType("wechat");
                user.setAddTime(OrderUtil.getSecondTimestampTwo());
                user.setLastTime(OrderUtil.getSecondTimestampTwo());
                user.setNickname(nickname);
                user.setAvatar(wxMpUser.getHeadImgUrl());
                user.setNowMoney(BigDecimal.ZERO);
                user.setBrokeragePrice(BigDecimal.ZERO);
                user.setIntegral(BigDecimal.ZERO);

                userService.save(user);


                //保存微信用户
                YxWechatUser yxWechatUser = new YxWechatUser();
                yxWechatUser.setAddTime(OrderUtil.getSecondTimestampTwo());
                yxWechatUser.setNickname(nickname);
                yxWechatUser.setOpenid(wxMpUser.getOpenId());
                int sub = 0;
                if (ObjectUtil.isNotNull(wxMpUser.getSubscribe()) && wxMpUser.getSubscribe()) sub = 1;
                yxWechatUser.setSubscribe(sub);
                yxWechatUser.setSex(wxMpUser.getSex());
                yxWechatUser.setLanguage(wxMpUser.getLanguage());
                yxWechatUser.setCity(wxMpUser.getCity());
                yxWechatUser.setProvince(wxMpUser.getProvince());
                yxWechatUser.setCountry(wxMpUser.getCountry());
                yxWechatUser.setHeadimgurl(wxMpUser.getHeadImgUrl());
                if (ObjectUtil.isNotNull(wxMpUser.getSubscribeTime())) {
                    yxWechatUser.setSubscribeTime(wxMpUser.getSubscribeTime().intValue());
                }
                if (StrUtil.isNotEmpty(wxMpUser.getUnionId())) {
                    yxWechatUser.setUnionid(wxMpUser.getUnionId());
                }
                if (StrUtil.isNotEmpty(wxMpUser.getRemark())) {
                    yxWechatUser.setUnionid(wxMpUser.getRemark());
                }
                if (ObjectUtil.isNotEmpty(wxMpUser.getGroupId())) {
                    yxWechatUser.setGroupid(wxMpUser.getGroupId());
                }
                yxWechatUser.setUid(user.getUid());

                wechatUserService.save(yxWechatUser);


                jwtUser = (JwtUser) userDetailsService.loadUserByUsername(wxMpUser.getOpenId());
            }


            //设置推广关系
            if (StrUtil.isNotEmpty(spread) && !spread.equals("NaN")) {
                //System.out.println("spread:"+spread);
                userService.setSpread(Integer.valueOf(spread),
                        jwtUser.getId().intValue());
            }

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(jwtUser.getUsername(),
                            "123456");

            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 生成令牌
            String token = tokenProvider.createToken(authentication);
            final JwtUser jwtUserT = (JwtUser) authentication.getPrincipal();
            // 保存在线信息
            onlineUserService.save(jwtUserT, token, request);

            Date expiresTime = tokenProvider.getExpirationDateFromToken(token);
            String expiresTimeStr = DateUtil.formatDateTime(expiresTime);

            Map<String, String> map = new LinkedHashMap<>();
            map.put("token", token);
            map.put("expires_time", expiresTimeStr);

            // 返回 token
            return ApiResult.ok(map);
        } catch (WxErrorException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return ApiResult.fail("授权失败");
        }

    }


    /**
     * 小程序登陆接口
     */
    @AnonymousAccess
    @PostMapping("/wxapp/auth")
    @ApiOperation(value = "小程序登陆", notes = "小程序登陆")
    public ApiResult<Object> login(@RequestParam(value = "code") String code,
                                   @RequestParam(value = "spread") String spread,
                                   @RequestParam(value = "encryptedData") String encryptedData,
                                   @RequestParam(value = "iv") String iv,
                                   HttpServletRequest request) {
        if (StringUtils.isBlank(code)) {
            return ApiResult.fail("请传code");
        }
        try {
            //读取redis配置
            String appId = RedisUtil.get("wxapp_appId");
            String secret = RedisUtil.get("wxapp_secret");
            if (StrUtil.isBlank(appId) || StrUtil.isBlank(secret)) {
                throw new ErrorRequestException("请先配置小程序");
            }
            WxMaDefaultConfigImpl wxMaConfig = new WxMaDefaultConfigImpl();
            wxMaConfig.setAppid(appId);
            wxMaConfig.setSecret(secret);


            wxMaService.setWxMaConfig(wxMaConfig);
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(code);
            YxWechatUser wechatUser = wechatUserService.getUserInfo(session.getOpenid());
            ;
            JwtUser jwtUser = null;
            if (ObjectUtil.isNotNull(wechatUser)) {
                YxUserQueryVo yxUserQueryVo = userService.getYxUserById(wechatUser.getUid());
                if (ObjectUtil.isNotNull(yxUserQueryVo)) {
                    jwtUser = (JwtUser) userDetailsService.loadUserByUsername(wechatUser.getOpenid());
                } else {
                    if (ObjectUtil.isNotNull(wechatUser)) {
                        wechatUserService.removeById(wechatUser.getUid());
                    }
                    if (ObjectUtil.isNotNull(yxUserQueryVo)) {
                        userService.removeById(yxUserQueryVo.getUid());
                    }
                    return ApiResult.fail(ApiCode.FAIL_AUTH, "授权失败");
                }

            } else {
                WxMaUserInfo wxMpUser = wxMaService.getUserService()
                        .getUserInfo(session.getSessionKey(), encryptedData, iv);
                //过滤掉表情
                String nickname = EmojiParser.removeAllEmojis(wxMpUser.getNickName());
                //用户保存
                YxUser user = new YxUser();
                user.setAccount(nickname);
                user.setUsername(wxMpUser.getOpenId());
                user.setPassword(passwordEncoder.encode("123456"));
                user.setPwd(passwordEncoder.encode("123456"));
                user.setPhone("");
                user.setUserType("routine");
                user.setAddTime(OrderUtil.getSecondTimestampTwo());
                user.setLastTime(OrderUtil.getSecondTimestampTwo());
                user.setNickname(nickname);
                user.setAvatar(wxMpUser.getAvatarUrl());
                user.setNowMoney(BigDecimal.ZERO);
                user.setBrokeragePrice(BigDecimal.ZERO);
                user.setIntegral(BigDecimal.ZERO);

                userService.save(user);


                //保存微信用户
                YxWechatUser yxWechatUser = new YxWechatUser();
                // System.out.println("wxMpUser:"+wxMpUser);
                yxWechatUser.setAddTime(OrderUtil.getSecondTimestampTwo());
                yxWechatUser.setNickname(nickname);
                yxWechatUser.setRoutineOpenid(wxMpUser.getOpenId());
                int sub = 0;
                yxWechatUser.setSubscribe(sub);
                yxWechatUser.setSex(Integer.valueOf(wxMpUser.getGender()));
                yxWechatUser.setLanguage(wxMpUser.getLanguage());
                yxWechatUser.setCity(wxMpUser.getCity());
                yxWechatUser.setProvince(wxMpUser.getProvince());
                yxWechatUser.setCountry(wxMpUser.getCountry());
                yxWechatUser.setHeadimgurl(wxMpUser.getAvatarUrl());
                if (StrUtil.isNotEmpty(wxMpUser.getUnionId())) {
                    yxWechatUser.setUnionid(wxMpUser.getUnionId());
                }
                yxWechatUser.setUid(user.getUid());

                wechatUserService.save(yxWechatUser);


                jwtUser = (JwtUser) userDetailsService.loadUserByUsername(wxMpUser.getOpenId());
            }


            //设置推广关系
            if (StrUtil.isNotEmpty(spread)) {
                userService.setSpread(Integer.valueOf(spread),
                        jwtUser.getId().intValue());
            }

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(jwtUser.getUsername(),
                            "123456");

            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 生成令牌
            String token = tokenProvider.createToken(authentication);
            final JwtUser jwtUserT = (JwtUser) authentication.getPrincipal();
            // 保存在线信息
            onlineUserService.save(jwtUserT, token, request);

            Date expiresTime = tokenProvider.getExpirationDateFromToken(token);
            String expiresTimeStr = DateUtil.formatDateTime(expiresTime);


            Map<String, String> map = new LinkedHashMap<>();
            map.put("token", token);
            map.put("expires_time", expiresTimeStr);

            // 返回 token
            return ApiResult.ok(map);
        } catch (WxErrorException e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.toString());
        }
    }


    @AnonymousAccess
    @PostMapping("/register/verify")
    @ApiOperation(value = "验证码发送", notes = "验证码发送")
    public ApiResult<String> verify(@Validated @RequestBody VerityParam param) {
        Boolean isTest = true;
        YxUser yxUser = userService.findByName(param.getPhone());
        if (param.getType().equals("register") && ObjectUtil.isNotNull(yxUser)) {
            return ApiResult.fail("手机号已注册");
        }
        if (param.getType().equals("login") && ObjectUtil.isNull(yxUser)) {
            return ApiResult.fail("账号不存在");
        }
        if (ObjectUtil.isNotNull(redisUtils.get("code_" + param.getPhone()))) {
            return ApiResult.fail("10分钟内有效:" + redisUtils.get("code_" + param.getPhone()).toString());
        }
        String code = RandomUtil.randomNumbers(6);
        redisUtils.set("code_" + param.getPhone(), code, 600L);
        if (isTest) {
            return ApiResult.fail("测试阶段验证码:" + code);
        }
        return ApiResult.ok("发送成功");
    }

    @AnonymousAccess
    @PostMapping("/register")
    @ApiOperation(value = "H5注册新用户", notes = "H5注册新用户")
    public ApiResult<String> register(@Validated @RequestBody RegParam param) {
        String code = redisUtils.get("code_" + param.getAccount()).toString();
        if (StrUtil.isEmpty(code)) {
            return ApiResult.fail("请先获取验证码");
        }

        if (!StrUtil.equals(code, param.getCaptcha())) {
            return ApiResult.fail("验证码错误");
        }

        YxUser yxUser = userService.findByName(param.getAccount());
        if (ObjectUtil.isNotNull(yxUser)) {
            return ApiResult.fail("用户已存在");
        }

        YxUser user = new YxUser();
        user.setAccount(param.getAccount());
        user.setUsername(param.getAccount());
        user.setPassword(passwordEncoder.encode(param.getPassword()));
        user.setPwd(passwordEncoder.encode(param.getPassword()));
        user.setPhone(param.getAccount());
        user.setUserType("h5");
        user.setAddTime(OrderUtil.getSecondTimestampTwo());
        user.setLastTime(OrderUtil.getSecondTimestampTwo());
        user.setNickname(param.getAccount());
        user.setAvatar("https://image.dayouqiantu.cn/5dc2c7f3a104c.png");
        user.setNowMoney(BigDecimal.ZERO);
        user.setBrokeragePrice(BigDecimal.ZERO);
        user.setIntegral(BigDecimal.ZERO);

        userService.save(user);

        return ApiResult.ok("注册成功");
    }


    @ApiOperation("获取用户信息")
    @GetMapping(value = "/info")
    public ApiResult<Object> getUserInfo() {
        JwtUser jwtUser = (JwtUser) userDetailsService.loadUserByUsername(SecurityUtils.getUsername());
        return ApiResult.ok(jwtUser);
    }


    @ApiOperation(value = "退出登录", notes = "退出登录")
    @AnonymousAccess
    @PostMapping(value = "/auth/logout")
    public ApiResult<Object> logout(HttpServletRequest request) {
        onlineUserService.logout(tokenProvider.getToken(request));
        return ApiResult.ok("退出成功");
    }
}
