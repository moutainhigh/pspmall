package co.yixiang.modules.user.web.controller;

import co.yixiang.common.api.ApiResult;
import co.yixiang.common.web.controller.BaseController;
import co.yixiang.modules.user.service.YxUserRechargeService;
import co.yixiang.modules.user.web.param.RechargeParam;
import co.yixiang.utils.SecurityUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * 用户充值 前端控制器
 * </p>
 *
 * @author hupeng
 * @since 2019-12-08
 */
@Slf4j
@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Api(value = "用户充值", tags = "用户充值", description = "用户充值")
public class UserRechargeController extends BaseController {


    private final YxUserRechargeService userRechargeService;

    /**
     * 公众号充值
     */
    @PostMapping("/recharge/wechat")
    @ApiOperation(value = "公众号充值",notes = "公众号充值",response = ApiResult.class)
    public ApiResult<Map<String,Object>> add(@Valid @RequestBody RechargeParam param){
        int uid = SecurityUtils.getUserId().intValue();

        Map<String,Object> map = new LinkedHashMap<>();
        map.put("id",null);
        return ApiResult.ok(map);
    }




}

