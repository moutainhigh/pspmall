package co.yixiang.modules.order.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import co.yixiang.common.rocketmq.MqProducer;
import co.yixiang.common.service.impl.BaseServiceImpl;
import co.yixiang.common.web.vo.Paging;
import co.yixiang.domain.AlipayConfig;
import co.yixiang.domain.vo.TradeVo;
import co.yixiang.exception.ErrorRequestException;
import co.yixiang.modules.activity.service.*;
import co.yixiang.modules.manage.service.YxExpressService;
import co.yixiang.modules.manage.web.dto.ChartDataDTO;
import co.yixiang.modules.manage.web.dto.OrderDataDTO;
import co.yixiang.modules.manage.web.dto.OrderTimeDataDTO;
import co.yixiang.modules.manage.web.param.OrderDeliveryParam;
import co.yixiang.modules.manage.web.param.OrderPriceParam;
import co.yixiang.modules.manage.web.param.OrderRefundParam;
import co.yixiang.modules.manage.web.vo.YxExpressQueryVo;
import co.yixiang.modules.monitor.service.RedisService;
import co.yixiang.modules.order.entity.YxStoreOrder;
import co.yixiang.modules.order.entity.YxStoreOrderCartInfo;
import co.yixiang.modules.order.mapper.YxStoreOrderMapper;
import co.yixiang.modules.order.mapping.OrderMap;
import co.yixiang.modules.order.service.YxStoreOrderCartInfoService;
import co.yixiang.modules.order.service.YxStoreOrderService;
import co.yixiang.modules.order.service.YxStoreOrderStatusService;
import co.yixiang.modules.order.web.dto.*;
import co.yixiang.modules.order.web.param.OrderParam;
import co.yixiang.modules.order.web.param.RefundParam;
import co.yixiang.modules.order.web.param.YxStoreOrderQueryParam;
import co.yixiang.modules.order.web.vo.YxStoreOrderQueryVo;
import co.yixiang.modules.shop.entity.YxStoreCart;
import co.yixiang.modules.shop.entity.YxStoreCouponUser;
import co.yixiang.modules.shop.mapper.YxStoreCartMapper;
import co.yixiang.modules.shop.mapper.YxStoreCouponUserMapper;
import co.yixiang.modules.shop.service.YxStoreCouponUserService;
import co.yixiang.modules.shop.service.YxStoreProductReplyService;
import co.yixiang.modules.shop.service.YxStoreProductService;
import co.yixiang.modules.shop.service.YxSystemConfigService;
import co.yixiang.modules.shop.web.vo.YxStoreCartQueryVo;
import co.yixiang.modules.user.entity.YxUser;
import co.yixiang.modules.user.entity.YxUserBill;
import co.yixiang.modules.user.entity.YxWechatUser;
import co.yixiang.modules.user.service.YxUserAddressService;
import co.yixiang.modules.user.service.YxUserBillService;
import co.yixiang.modules.user.service.YxUserService;
import co.yixiang.modules.user.service.YxWechatUserService;
import co.yixiang.modules.user.web.vo.YxUserAddressQueryVo;
import co.yixiang.modules.user.web.vo.YxUserQueryVo;
import co.yixiang.modules.user.web.vo.YxWechatUserQueryVo;
import co.yixiang.modules.wechat.entity.YxWechatTemplate;
import co.yixiang.modules.wechat.service.YxWechatTemplateService;
import co.yixiang.mp.service.WxMpTemplateMessageService;
import co.yixiang.service.AlipayService;
import co.yixiang.utils.OrderUtil;
import co.yixiang.utils.RedisUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayRefundRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;


/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author hupeng
 * @since 2019-10-27
 */

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class YxStoreOrderServiceImpl extends BaseServiceImpl<YxStoreOrderMapper, YxStoreOrder> implements YxStoreOrderService {

    @Autowired
    private YxStoreOrderMapper yxStoreOrderMapper;

    @Autowired
    private  YxSystemConfigService systemConfigService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private OrderMap orderMap;

    @Autowired
    private YxUserService userService;

    @Autowired
    private YxUserAddressService userAddressService;

    @Autowired
    private YxStoreProductService productService;

    @Autowired
    private YxStoreOrderCartInfoService orderCartInfoService;

    @Autowired
    private YxStoreCartMapper storeCartMapper;

    @Autowired
    private YxStoreOrderStatusService orderStatusService;

    @Autowired
    private YxUserBillService billService;

    @Autowired
    private YxStoreProductReplyService storeProductReplyService;

    @Autowired
    private  WxPayService wxPayService;

    @Autowired
    private YxWechatUserService wechatUserService;

    @Autowired
    private YxStoreCouponUserService couponUserService;

    @Autowired
    private YxStoreCouponUserMapper yxStoreCouponUserMapper;


    @Autowired
    private YxStoreCombinationService combinationService;

    @Autowired
    private YxStoreSeckillService storeSeckillService;

    @Autowired
    private YxStoreBargainService storeBargainService;

    @Autowired
    private YxStoreBargainUserService storeBargainUserService;

    @Autowired
    private YxStorePinkService pinkService;

    @Autowired
    private WxMpTemplateMessageService templateMessageService;

    @Autowired
    private YxWechatTemplateService yxWechatTemplateService;

    @Autowired
    private YxExpressService expressService;

    @Autowired
    private AlipayService alipayService;


    @Autowired
    private MqProducer mqProducer;


    /**
     * 订单退款
     * @param param
     */
    @Override
    public void orderRefund(OrderRefundParam param) {

        YxStoreOrderQueryVo orderQueryVo = getOrderInfo(param.getOrderId(),0);
        if(ObjectUtil.isNull(orderQueryVo)) throw new ErrorRequestException("订单不存在");

        YxUserQueryVo userQueryVo = userService.getYxUserById(orderQueryVo.getUid());
        if(ObjectUtil.isNull(userQueryVo)) throw new ErrorRequestException("用户不存在");

        if(param.getPrice() > orderQueryVo.getPayPrice().doubleValue()) throw new ErrorRequestException("退款金额不正确");

        YxStoreOrder storeOrder = new YxStoreOrder();
        //修改状态
        storeOrder.setId(orderQueryVo.getId());

        if(param.getType() == 2){
            storeOrder.setRefundStatus(0);
            yxStoreOrderMapper.updateById(storeOrder);
            return;
        }

        //根据支付类型不同退款不同
        if(orderQueryVo.getPayType().equals("yue")){
            storeOrder.setRefundStatus(2);
            storeOrder.setRefundPrice(BigDecimal.valueOf(param.getPrice()));
            yxStoreOrderMapper.updateById(storeOrder);
            //退款到余额
            userService.incMoney(orderQueryVo.getUid(),param.getPrice());

            //增加流水
            YxUserBill userBill = new YxUserBill();
            userBill.setUid(orderQueryVo.getUid());
            userBill.setLinkId(orderQueryVo.getId().toString());
            userBill.setPm(1);
            userBill.setTitle("商品退款");
            userBill.setCategory("now_money");
            userBill.setType("pay_product_refund");
            userBill.setNumber(BigDecimal.valueOf(param.getPrice()));
            userBill.setBalance(NumberUtil.add(param.getPrice(),userQueryVo.getNowMoney()));
            userBill.setMark("订单退款到余额");
            userBill.setAddTime(OrderUtil.getSecondTimestampTwo());
            userBill.setStatus(1);
            billService.save(userBill);


            orderStatusService.create(orderQueryVo.getId(),"order_edit","退款给用户："+param.getPrice() +"元");
        }else{
            String apiUrl = RedisUtil.get("api_url");
            if(StrUtil.isBlank(apiUrl)) throw new ErrorRequestException("请配置api地址");
            //读取redis配置
            String appId = RedisUtil.get("wxpay_appId");
            String mchId = RedisUtil.get("wxpay_mchId");
            String mchKey = RedisUtil.get("wxpay_mchKey");
            String keyPath = RedisUtil.get("wxpay_keyPath");

            if(StrUtil.isBlank(appId) || StrUtil.isBlank(mchId) || StrUtil.isBlank(mchKey)){
                throw new ErrorRequestException("请配置微信支付");
            }
            if(StrUtil.isBlank(keyPath)){
                throw new ErrorRequestException("请配置微信支付证书");
            }
            WxPayRefundRequest wxPayRefundRequest = new WxPayRefundRequest();
            BigDecimal bigDecimal = new BigDecimal("100");
            wxPayRefundRequest.setTotalFee(bigDecimal.multiply(orderQueryVo.getPayPrice()).intValue());//订单总金额
            wxPayRefundRequest.setOutTradeNo(param.getOrderId());
            wxPayRefundRequest.setOutRefundNo(param.getOrderId());
            wxPayRefundRequest.setRefundFee(bigDecimal.multiply(orderQueryVo.getPayPrice()).intValue());//退款金额
            wxPayRefundRequest.setOpUserId(mchId); //操作人默认商户号当前
            wxPayRefundRequest.setNotifyUrl(apiUrl+"/api/notify/refund");

            WxPayConfig wxPayConfig = new WxPayConfig();
            wxPayConfig.setAppId(appId);
            wxPayConfig.setMchId(mchId);
            wxPayConfig.setMchKey(mchKey);
            wxPayConfig.setKeyPath(keyPath);
            wxPayService.setConfig(wxPayConfig);
            try {
                wxPayService.refund(wxPayRefundRequest);
            } catch (WxPayException e) {
                log.info("refund-error:{}",e.getMessage());
            }
        }

        //模板消息通知
        String siteUrl = RedisUtil.get("site_url");
        YxWechatUserQueryVo wechatUser =  wechatUserService.getYxWechatUserById(orderQueryVo.getUid());
        if(ObjectUtil.isNotNull(wechatUser)){
            YxWechatTemplate WechatTemplate = yxWechatTemplateService.getOne(
                    new QueryWrapper<YxWechatTemplate>().eq("tempkey","OPENTM410119152"));
            Map<String,String> map = new HashMap<>();
            map.put("first","您在yshop的订单退款申请被通过，钱款将很快还至您的支付账户。");
            map.put("keyword1",orderQueryVo.getOrderId());//订单号
            map.put("keyword2",orderQueryVo.getPayPrice().toString());
            map.put("keyword3",OrderUtil.stampToDate(orderQueryVo.getAddTime().toString()));
            map.put("remark","yshop电商系统为你服务！");
            templateMessageService.sendWxMpTemplateMessage( wechatUser.getOpenid()
                    ,WechatTemplate.getTempid(),
                    siteUrl+"/order/detail/"+orderQueryVo.getOrderId(),map);
        }


    }

    /**
     * 订单发货
     * @param param
     */
    @Override
    public void orderDelivery(OrderDeliveryParam param) {
        YxStoreOrderQueryVo orderQueryVo = getOrderInfo(param.getOrderId(),0);
        if(ObjectUtil.isNull(orderQueryVo)) throw new ErrorRequestException("订单不存在");

        if(orderQueryVo.getStatus() != 0 || orderQueryVo.getPaid() == 0) throw new ErrorRequestException("订单状态错误");

        YxExpressQueryVo expressQueryVo = expressService
                .getYxExpressById(Integer.valueOf(param.getDeliveryName()));
        if(ObjectUtil.isNull(expressQueryVo)) throw new ErrorRequestException("请后台先添加快递公司");

        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setId(orderQueryVo.getId());
        storeOrder.setStatus(1);
        storeOrder.setDeliveryId(param.getDeliveryId());
        storeOrder.setDeliveryName(expressQueryVo.getName());
        storeOrder.setDeliveryType(param.getDeliveryType());
        storeOrder.setDeliverySn(expressQueryVo.getCode());

        yxStoreOrderMapper.updateById(storeOrder);

        //增加状态
        orderStatusService.create(storeOrder.getId(),"delivery_goods",
                "已发货 快递公司："+expressQueryVo.getName()+"快递单号：" +param.getDeliveryId());

        //模板消息通知
        String siteUrl = RedisUtil.get("site_url");
        YxWechatUserQueryVo wechatUser =  wechatUserService.getYxWechatUserById(orderQueryVo.getUid());
        if(ObjectUtil.isNotNull(wechatUser)){
            YxWechatTemplate WechatTemplate = yxWechatTemplateService.getOne(
                    new QueryWrapper<YxWechatTemplate>().eq("tempkey","OPENTM200565259"));
            //付款成功微信模板通知用户
            Map<String,String> map = new HashMap<>();
            map.put("first","亲，宝贝已经启程了，好想快点来到你身边。");
            map.put("keyword1",storeOrder.getOrderId());//订单号
            map.put("keyword2",expressQueryVo.getName());
            map.put("keyword3",param.getDeliveryId());
            map.put("remark","yshop电商系统为你服务！");
            templateMessageService.sendWxMpTemplateMessage( wechatUser.getOpenid()
                    ,WechatTemplate.getTempid(),
                    siteUrl+"/order/detail/"+orderQueryVo.getOrderId(),map);
        }

    }

    /**
     * 修改订单价格
     * @param param
     */
    @Override
    public void editOrderPrice(OrderPriceParam param) {
        YxStoreOrderQueryVo orderQueryVo = getOrderInfo(param.getOrderId(),0);
        if(ObjectUtil.isNull(orderQueryVo)) throw new ErrorRequestException("订单不存在");

        if(orderQueryVo.getPayPrice().doubleValue() == param.getPrice()) return;

        if(orderQueryVo.getPaid() > 0) throw new ErrorRequestException("订单状态错误");


        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setId(orderQueryVo.getId());
        storeOrder.setPayPrice(BigDecimal.valueOf(param.getPrice()));

        yxStoreOrderMapper.updateById(storeOrder);

        //增加状态
        orderStatusService.create(storeOrder.getId(),"order_edit","修改实际支付金额");


    }

    /**
     * 获取拼团订单
     * @param pid
     * @param uid
     * @param type
     * @return
     */
    @Override
    public YxStoreOrder getOrderPink(int pid, int uid,int type) {
        QueryWrapper<YxStoreOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("is_del",0).eq("uid",uid).eq("pink_id",pid);
        if(type == 0) wrapper.eq("refund_status",0);
        return yxStoreOrderMapper.selectOne(wrapper);
    }

    /**
     * 退回优惠券
     * @param order
     */
    @Override
    public void regressionCoupon(YxStoreOrderQueryVo order) {
        if(order.getPaid() > 0 || order.getStatus() == -2 || order.getIsDel() ==1){
            return;
        }

        QueryWrapper<YxStoreCouponUser> wrapper= new QueryWrapper<>();
        if(order.getCouponId() > 0){
            wrapper.eq("id",order.getCouponId()).eq("status",1).eq("uid",order.getUid());
            YxStoreCouponUser couponUser = yxStoreCouponUserMapper.selectOne(wrapper);

            if(ObjectUtil.isNotNull(couponUser)){
                YxStoreCouponUser storeCouponUser = new YxStoreCouponUser();
                QueryWrapper<YxStoreCouponUser> wrapperT= new QueryWrapper<>();
                wrapperT.eq("id",order.getCouponId()).eq("uid",order.getUid());
                storeCouponUser.setStatus(0);
                storeCouponUser.setUseTime(0);
                yxStoreCouponUserMapper.update(storeCouponUser,wrapperT);
            }
        }

    }

    /**
     * 退回库存
     * @param order
     */
    @Override
    public void regressionStock(YxStoreOrderQueryVo order) {
        if(order.getPaid() > 0 || order.getStatus() == -2 || order.getIsDel() ==1){
            return;
        }
        QueryWrapper<YxStoreOrderCartInfo> wrapper= new QueryWrapper<>();
        wrapper.in("cart_id", Arrays.asList(order.getCartId().split(",")));

        List<YxStoreOrderCartInfo> cartInfoList =  orderCartInfoService.list(wrapper);
        for (YxStoreOrderCartInfo cartInfo : cartInfoList) {
            YxStoreCartQueryVo cart = JSONObject.parseObject(cartInfo.getCartInfo()
                    ,YxStoreCartQueryVo.class);
            if(order.getCombinationId() > 0){//拼团
                combinationService.incStockDecSales(cart.getCartNum(),order.getCombinationId());
            }else if(order.getSeckillId() > 0){//秒杀
                storeSeckillService.incStockDecSales(cart.getCartNum(),order.getSeckillId());
            }else if(order.getBargainId() > 0){//砍价
                storeBargainService.incStockDecSales(cart.getCartNum(),order.getBargainId());
            }else{
                productService.incProductStock(cart.getCartNum(),cart.getProductId()
                        ,cart.getProductAttrUnique());
            }

        }
    }

    /**
     * 退回积分
     * @param order
     */
    @Override
    public void regressionIntegral(YxStoreOrderQueryVo order) {
        if(order.getPaid() > 0 || order.getStatus() == -2 || order.getIsDel() ==1){
            return;
        }
        if(order.getUseIntegral().doubleValue() <= 0) return;

        if(order.getStatus() != -2 && order.getRefundStatus() != 2
                && ObjectUtil.isNotNull(order.getBackIntegral())
                && order.getBackIntegral().doubleValue() >= order.getUseIntegral().doubleValue()){
            return;
        }

        YxUserQueryVo userQueryVo = userService
                .getYxUserById(order.getUid());

        //增加积分
        userService.incIntegral(order.getUid(),order.getUseIntegral().doubleValue());

        //增加流水
        YxUserBill userBill = new YxUserBill();
        userBill.setUid(order.getUid());
        userBill.setTitle("积分回退");
        userBill.setLinkId(order.getId().toString());
        userBill.setCategory("integral");
        userBill.setType("deduction");
        userBill.setNumber(order.getUseIntegral());
        userBill.setBalance(userQueryVo.getIntegral());
        userBill.setMark("购买商品失败,回退积分");
        userBill.setStatus(1);
        userBill.setPm(1);
        userBill.setAddTime(OrderUtil.getSecondTimestampTwo());
        billService.save(userBill);

        //更新回退积分
        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setBackIntegral(order.getUseIntegral());
        storeOrder.setId(order.getId());
        yxStoreOrderMapper.updateById(storeOrder);
    }

    /**
     * 未付款取消订单
     * @param orderId
     * @param uid
     */
    @Override
    public void cancelOrder(String orderId, int uid) {
        YxStoreOrderQueryVo order = getOrderInfo(orderId,uid);
        if(ObjectUtil.isNull(order)) throw new ErrorRequestException("订单不存在");
        if(order.getIsDel() == 1)throw new ErrorRequestException("订单已取消");
        regressionIntegral(order);

        regressionStock(order);

        regressionCoupon(order);

        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setIsDel(1);
        storeOrder.setId(order.getId());
        yxStoreOrderMapper.updateById(storeOrder);
    }

    /**
     * 系统自动主动取消未付款取消订单
     * @param orderId
     */
    @Override
    public void cancelOrderByTask(int orderId) {
        YxStoreOrderQueryVo order = null;
        try {
            order = getYxStoreOrderById(orderId);

            if(ObjectUtil.isNull(order)) throw new ErrorRequestException("订单不存在");

            if(order.getIsDel() == 1)throw new ErrorRequestException("订单已取消");

            regressionIntegral(order);

            regressionStock(order);

            regressionCoupon(order);

            YxStoreOrder storeOrder = new YxStoreOrder();
            storeOrder.setIsDel(1);
            storeOrder.setId(order.getId());
            yxStoreOrderMapper.updateById(storeOrder);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 奖励积分
     * @param order
     */
    @Override
    public void gainUserIntegral(YxStoreOrderQueryVo order) {
        if(order.getGainIntegral().intValue() > 0){
            YxUserQueryVo userQueryVo = userService
                    .getYxUserById(order.getUid());

            YxUser user = new YxUser();

            user.setIntegral(NumberUtil.add(userQueryVo.getIntegral(),
                    order.getGainIntegral()));
            user.setUid(order.getUid());
            userService.updateById(user);

            YxUserBill userBill = new YxUserBill();
            userBill.setUid(order.getUid());
            userBill.setTitle("购买商品赠送积分");
            userBill.setLinkId(order.getId().toString());
            userBill.setCategory("integral");
            userBill.setType("gain");
            userBill.setNumber(order.getGainIntegral());
            userBill.setBalance(userQueryVo.getIntegral());
            userBill.setMark("购买商品赠送");
            userBill.setStatus(1);
            userBill.setPm(1);
            userBill.setAddTime(OrderUtil.getSecondTimestampTwo());
            billService.save(userBill);

        }
    }


    /**
     * 删除订单
     * @param orderId
     * @param uid
     */
    @Override
    public void removeOrder(String orderId, int uid) {
        YxStoreOrderQueryVo order = getOrderInfo(orderId,uid);
        if(ObjectUtil.isNull(order)) throw new ErrorRequestException("订单不存在");
        order = handleOrder(order);
        if(!order.get_status().get_type().equals("0") &&
                !order.get_status().get_type().equals("-2") &&
                !order.get_status().get_type().equals("4")) {
            throw new ErrorRequestException("该订单无法删除");
        }

        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setIsDel(1);
        storeOrder.setId(order.getId());
        yxStoreOrderMapper.updateById(storeOrder);

        //增加状态
        orderStatusService.create(order.getId(),"remove_order","删除订单");
    }

    /**
     * 订单确认收货
     * @param orderId
     * @param uid
     */
    @Override
    public void takeOrder(String orderId, int uid) {
        YxStoreOrderQueryVo order = getOrderInfo(orderId,uid);
        if(ObjectUtil.isNull(order)) throw new ErrorRequestException("订单不存在");
        order = handleOrder(order);
        if(!order.get_status().get_type().equals("2")) throw new ErrorRequestException("订单状态错误");

        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setStatus(2);
        storeOrder.setId(order.getId());
        yxStoreOrderMapper.updateById(storeOrder);

        //增加状态
        orderStatusService.create(order.getId(),"user_take_delivery","用户已收货");

        //奖励积分
        gainUserIntegral(order);

        //分销计算
        userService.backOrderBrokerage(order);

    }

    /**
     * 申请退款
     * @param param
     * @param uid
     */
    @Override
    public void orderApplyRefund(RefundParam param, int uid) {
        YxStoreOrderQueryVo order = getOrderInfo(param.getUni(),uid);
        if(ObjectUtil.isNull(order)) throw new ErrorRequestException("订单不存在");
        if(order.getRefundStatus() == 2) throw new ErrorRequestException("订单已退款");
        if(order.getRefundStatus() == 1) throw new ErrorRequestException("正在申请退款中");
        if(order.getStatus() == 1 ) throw new ErrorRequestException("订单当前无法退款");

        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setRefundStatus(1);
        storeOrder.setRefundReasonTime(OrderUtil.getSecondTimestampTwo());
        storeOrder.setRefundReasonWapExplain(param.getRefund_reason_wap_explain());
        storeOrder.setRefundReasonWapImg(param.getRefund_reason_wap_img());
        storeOrder.setRefundReasonWap(param.getText());
        storeOrder.setId(order.getId());
        yxStoreOrderMapper.updateById(storeOrder);

        //增加状态
        orderStatusService.create(order.getId(),"apply_refund","用户申请退款，原因："+param.getText());

        //todo 推送
    }

    /**
     * 订单列表
     * @param uid
     * @param type
     * @param page
     * @param limit
     * @return
     */
    @Override
    public List<YxStoreOrderQueryVo> orderList(int uid, int type, int page, int limit) {
        QueryWrapper<YxStoreOrder> wrapper= new QueryWrapper<>();
        if(uid > 0) wrapper.eq("uid",uid);
        wrapper.eq("is_del",0).orderByDesc("add_time");

        switch (type){
            case 0://未支付
                wrapper.eq("paid",0).eq("refund_status",0).eq("status",0);
                break;
            case 1://待发货
                wrapper.eq("paid",1).eq("refund_status",0).eq("status",0);
                break;
            case 2://待收货
                wrapper.eq("paid",1).eq("refund_status",0).eq("status",1);
                break;
            case 3://待评价
                wrapper.eq("paid",1).eq("refund_status",0).eq("status",2);
                break;
            case 4://已完成
                wrapper.eq("paid",1).eq("refund_status",0).eq("status",3);
                break;
            case -1://退款中
                wrapper.eq("paid",1).eq("refund_status",1);
                break;
            case -2://已退款
                wrapper.eq("paid",0).eq("refund_status",2);
                break;
            case -3://退款
                String[] strs = {"1","2"};
                wrapper.eq("paid",1).in("refund_status",Arrays.asList(strs));
                break;
        }

        Page<YxStoreOrder> pageModel = new Page<>(page, limit);

        IPage<YxStoreOrder> pageList = yxStoreOrderMapper.selectPage(pageModel,wrapper);
        List<YxStoreOrderQueryVo> list = orderMap.toDto(pageList.getRecords());
        List<YxStoreOrderQueryVo> newList = new ArrayList<>();
        for (YxStoreOrderQueryVo order : list) {
            YxStoreOrderQueryVo orderQueryVo = handleOrder(order);
            newList.add(orderQueryVo);
        }

        return newList;
    }

    /**
     * chart图标统计
     * @param cate
     * @param type
     * @return
     */
    @Override
    public Map<String,Object> chartCount(int cate,int type) {
        int today = OrderUtil.dateToTimestampT(DateUtil.beginOfDay(new Date()));
        int yesterday = OrderUtil.dateToTimestampT(DateUtil.beginOfDay(DateUtil.
                yesterday()));
        int lastWeek = OrderUtil.dateToTimestampT(DateUtil.beginOfDay(DateUtil.lastWeek()));
        int nowMonth = OrderUtil.dateToTimestampT(DateUtil
                .beginOfMonth(new Date()));
        double price = 0d;
        List<ChartDataDTO> list = null;
        QueryWrapper<YxStoreOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("paid",1).eq("refund_status",0).eq("is_del",0);

        switch (cate){
            case 1: //今天
                wrapper.ge("pay_time",today);
                break;
            case 2: //昨天
                wrapper.lt("pay_time",today).ge("pay_time",yesterday);
                break;
            case 3: //上周
                wrapper.ge("pay_time",lastWeek);
                break;
            case 4: //本月
                wrapper.ge("pay_time",nowMonth);
                break;
        }
        if(type == 1){
            list = yxStoreOrderMapper.chartList(wrapper);
            price = yxStoreOrderMapper.todayPrice(wrapper);
        }else{
            list = yxStoreOrderMapper.chartListT(wrapper);
            price = yxStoreOrderMapper.selectCount(wrapper).doubleValue();
        }

        Map<String,Object> map = new LinkedHashMap<>();
        map.put("chart",list);
        map.put("time",price);
        return map;
    }

    /**
     * 获取 今日 昨日 本月 订单金额
     * @return
     */
    @Override
    public OrderTimeDataDTO getOrderTimeData() {

        int today = OrderUtil.dateToTimestampT(DateUtil.beginOfDay(new Date()));
        int yesterday = OrderUtil.dateToTimestampT(DateUtil.beginOfDay(DateUtil.
                yesterday()));
        int nowMonth = OrderUtil.dateToTimestampT(DateUtil
                .beginOfMonth(new Date()));
        OrderTimeDataDTO orderTimeDataDTO = new OrderTimeDataDTO();

        //今日成交额
        QueryWrapper<YxStoreOrder> wrapperOne = new QueryWrapper<>();
        wrapperOne.ge("pay_time",today).eq("paid",1)
                .eq("refund_status",0).eq("is_del",0);
        orderTimeDataDTO.setTodayPrice(yxStoreOrderMapper.todayPrice(wrapperOne));
        //今日订单数
        orderTimeDataDTO.setTodayCount(yxStoreOrderMapper.selectCount(wrapperOne));

        //昨日成交额
        QueryWrapper<YxStoreOrder> wrapperTwo = new QueryWrapper<>();
        wrapperTwo.lt("pay_time",today).ge("pay_time",yesterday).eq("paid",1)
                .eq("refund_status",0).eq("is_del",0);
        orderTimeDataDTO.setProPrice(yxStoreOrderMapper.todayPrice(wrapperTwo));
        //昨日订单数
        orderTimeDataDTO.setProCount(yxStoreOrderMapper.selectCount(wrapperTwo));

        //本月成交额
        QueryWrapper<YxStoreOrder> wrapperThree = new QueryWrapper<>();
        wrapperThree.ge("pay_time",nowMonth).eq("paid",1)
                .eq("refund_status",0).eq("is_del",0);
        orderTimeDataDTO.setMonthPrice(yxStoreOrderMapper.todayPrice(wrapperThree));
        //本月订单数
        orderTimeDataDTO.setMonthCount(yxStoreOrderMapper.selectCount(wrapperThree));


        return orderTimeDataDTO;
    }

    /**
     * 订单每月统计数据
     * @param page
     * @param limit
     * @return
     */
    @Override
    public List<OrderDataDTO> getOrderDataPriceCount(int page, int limit) {
        Page<YxStoreOrder> pageModel = new Page<>(page, limit);
        return yxStoreOrderMapper.getOrderDataPriceList(pageModel);
    }

    /**
     * 获取某个用户的订单统计数据
     * @param uid uid>0 取用户 否则取所有
     * @return
     */
    @Override
    public OrderCountDTO orderData(int uid) {

        OrderCountDTO countDTO = new OrderCountDTO();
        //订单支付没有退款 数量
        QueryWrapper<YxStoreOrder> wrapperOne = new QueryWrapper<>();
        if(uid > 0 ) wrapperOne.eq("uid",uid);
        wrapperOne.eq("is_del",0).eq("paid",1).eq("refund_status",0);
        countDTO.setOrderCount(yxStoreOrderMapper.selectCount(wrapperOne));

        //订单支付没有退款 支付总金额
        countDTO.setSumPrice(yxStoreOrderMapper.sumPrice(uid));

        //订单待支付 数量
        QueryWrapper<YxStoreOrder> wrapperTwo = new QueryWrapper<>();
        if(uid > 0 ) wrapperTwo.eq("uid",uid);
        wrapperTwo.eq("is_del",0).eq("paid",0)
                .eq("refund_status",0).eq("status",0);
        countDTO.setUnpaidCount(yxStoreOrderMapper.selectCount(wrapperTwo));

        //订单待发货 数量
        QueryWrapper<YxStoreOrder> wrapperThree = new QueryWrapper<>();
        if(uid > 0 ) wrapperThree.eq("uid",uid);
        wrapperThree.eq("is_del",0).eq("paid",1)
                .eq("refund_status",0).eq("status",0);
        countDTO.setUnshippedCount(yxStoreOrderMapper.selectCount(wrapperThree));

        //订单待收货 数量
        QueryWrapper<YxStoreOrder> wrapperFour = new QueryWrapper<>();
        if(uid > 0 ) wrapperFour.eq("uid",uid);
        wrapperFour.eq("is_del",0).eq("paid",1)
                .eq("refund_status",0).eq("status",1);
        countDTO.setReceivedCount(yxStoreOrderMapper.selectCount(wrapperFour));

        //订单待评价 数量
        QueryWrapper<YxStoreOrder> wrapperFive = new QueryWrapper<>();
        if(uid > 0 ) wrapperFive.eq("uid",uid);
        wrapperFive.eq("is_del",0).eq("paid",1)
                .eq("refund_status",0).eq("status",2);
        countDTO.setEvaluatedCount(yxStoreOrderMapper.selectCount(wrapperFive));

        //订单已完成 数量
        QueryWrapper<YxStoreOrder> wrapperSix= new QueryWrapper<>();
        if(uid > 0 ) wrapperSix.eq("uid",uid);
        wrapperSix.eq("is_del",0).eq("paid",1)
                .eq("refund_status",0).eq("status",3);
        countDTO.setCompleteCount(yxStoreOrderMapper.selectCount(wrapperSix));

        //订单退款
        QueryWrapper<YxStoreOrder> wrapperSeven= new QueryWrapper<>();
        if(uid > 0 ) wrapperSeven.eq("uid",uid);
        String[] strArr = {"1","2"};
        wrapperSeven.eq("is_del",0).eq("paid",1)
                .in("refund_status",Arrays.asList(strArr));
        countDTO.setRefundCount(yxStoreOrderMapper.selectCount(wrapperSeven));


        return countDTO;
    }

    /**
     * 处理订单返回的状态
     * @param order order
     * @return
     */
    @Override
    public YxStoreOrderQueryVo handleOrder(YxStoreOrderQueryVo order) {
        QueryWrapper<YxStoreOrderCartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("oid",order.getId());
        List<YxStoreOrderCartInfo> cartInfos = orderCartInfoService.list(wrapper);

        List<YxStoreCartQueryVo> cartInfo = new ArrayList<>();
        for (YxStoreOrderCartInfo info : cartInfos) {
            YxStoreCartQueryVo cartQueryVo = JSON.parseObject(info.getCartInfo(),YxStoreCartQueryVo.class);
            cartQueryVo.setUnique(info.getUnique());
            //新增是否评价字段
            cartQueryVo.setIsReply(storeProductReplyService.replyCount(info.getUnique()));
            cartInfo.add(cartQueryVo);
        }
        order.setCartInfo(cartInfo);
        StatusDTO statusDTO = new StatusDTO();
        if(order.getPaid() == 0){
            statusDTO.set_class("nobuy");
            statusDTO.set_msg("请完成支付");
            statusDTO.set_type("0");
            statusDTO.set_title("未支付");
        }else if(order.getRefundStatus() == 1){
            statusDTO.set_class("state-sqtk");
            statusDTO.set_msg("商家审核中,请耐心等待");
            statusDTO.set_type("-1");
            statusDTO.set_title("申请退款中");
        }else if(order.getRefundStatus() == 2){
            statusDTO.set_class("state-sqtk");
            statusDTO.set_msg("已为您退款,感谢您的支持");
            statusDTO.set_type("-2");
            statusDTO.set_title("已退款");
        }else if(order.getStatus() == 0){
            //todo 拼团
            //todo 店铺核销
            if(order.getPinkId() > 0){
                if(pinkService.pinkIngCount(order.getPinkId()) > 0){
                    statusDTO.set_class("state-nfh");
                    statusDTO.set_msg("待其他人参加拼团");
                    statusDTO.set_type("1");
                    statusDTO.set_title("拼团中");
                }else{
                    statusDTO.set_class("state-nfh");
                    statusDTO.set_msg("商家未发货,请耐心等待");
                    statusDTO.set_type("1");
                    statusDTO.set_title("未发货");
                }
            }else{
                statusDTO.set_class("state-nfh");
                statusDTO.set_msg("商家未发货,请耐心等待");
                statusDTO.set_type("1");
                statusDTO.set_title("未发货");
            }

        }else if(order.getStatus() == 1){
            statusDTO.set_class("state-ysh");
            statusDTO.set_msg("服务商已发货");
            statusDTO.set_type("2");
            statusDTO.set_title("待收货");
        }else if(order.getStatus() == 2){
            statusDTO.set_class("state-ypj");
            statusDTO.set_msg("已收货,快去评价一下吧");
            statusDTO.set_type("3");
            statusDTO.set_title("待评价");
        }else if(order.getStatus() == 3){
            statusDTO.set_class("state-ytk");
            statusDTO.set_msg("交易完成,感谢您的支持");
            statusDTO.set_type("4");
            statusDTO.set_title("交易完成");
        }

        if(order.getPayType().equals("weixin")){
            statusDTO.set_payType("微信支付");
        }else{
            statusDTO.set_payType("余额支付");
        }

        order.set_status(statusDTO);


        return order;
    }

    /**
     * 支付成功后操作
     * @param orderId 订单号
     * @param payType 支付方式
     */
    @Override
    public void paySuccess(String orderId, String payType) {
        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,0);

        //更新订单状态
        QueryWrapper<YxStoreOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id",orderId);
        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setPaid(1);
        storeOrder.setPayType(payType);
        storeOrder.setPayTime(OrderUtil.getSecondTimestampTwo());
        yxStoreOrderMapper.update(storeOrder,wrapper);

        //增加用户购买次数
        userService.incPayCount(orderInfo.getUid());
        //增加状态
        orderStatusService.create(orderInfo.getId(),"pay_success","用户付款成功");
        //拼团
        if(orderInfo.getCombinationId() > 0) pinkService.createPink(orderInfo);

        //砍价
        if(orderInfo.getBargainId() > 0) {
            storeBargainUserService.setBargainUserStatus(orderInfo.getBargainId(),
                    orderInfo.getUid());
        }
        //模板消息推送
        //读取redis配置
        String siteUrl = RedisUtil.get("site_url");
        YxWechatUserQueryVo wechatUser =  wechatUserService.getYxWechatUserById(orderInfo.getUid());
        if(ObjectUtil.isNotNull(wechatUser)){
            YxWechatTemplate WechatTemplate = yxWechatTemplateService.getOne(
                    new QueryWrapper<YxWechatTemplate>().eq("tempkey","OPENTM207791277"));
            //付款成功微信模板通知用户
            Map<String,String> map = new HashMap<>();
            map.put("first","您的订单已支付成功，我们会尽快为您发货。");
            map.put("keyword1",orderInfo.getOrderId());//订单号
            map.put("keyword2",orderInfo.getPayPrice().toString());
            map.put("remark","yshop电商系统为你服务！");
            templateMessageService.sendWxMpTemplateMessage( wechatUser.getOpenid()
                    ,WechatTemplate.getTempid(),
                    siteUrl+"/order/detail/"+orderInfo.getOrderId(),map);
        }

    }


    /**
     * 支付宝支付
     * @param orderId,支付宝支付 本系统已经集成，请自行根据下面找到代码整合下即可
     * @return
     */
    @Override
    public String aliPay(String orderId) throws Exception {
        AlipayConfig alipay = alipayService.find();
        if(ObjectUtil.isNull(alipay)) throw new ErrorRequestException("请先配置支付宝");
        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,0);
        if(ObjectUtil.isNull(orderInfo)) throw new ErrorRequestException("订单不存在");
        if(orderInfo.getPaid() == 1) throw new ErrorRequestException("该订单已支付");

        if(orderInfo.getPayPrice().doubleValue() <= 0) throw new ErrorRequestException("该支付无需支付");
        TradeVo trade = new TradeVo();
        trade.setOutTradeNo(orderId);
        String payUrl = alipayService.toPayAsWeb(alipay,trade);
        return payUrl;
    }

    /**
     * 微信H5支付
     * @param orderId
     * @return
     * @throws WxPayException
     */
    @Override
    public WxPayMwebOrderResult wxH5Pay(String orderId) throws WxPayException {

        String apiUrl = systemConfigService.getData("api_url");
        if(StrUtil.isBlank(apiUrl)) throw new ErrorRequestException("请配置api地址");

        //读取redis配置
        String appId = RedisUtil.get("wxpay_appId");
        String mchId = RedisUtil.get("wxpay_mchId");
        String mchKey = RedisUtil.get("wxpay_mchKey");
        if(StrUtil.isBlank(appId) || StrUtil.isBlank(mchId) || StrUtil.isBlank(mchKey)){
            throw new ErrorRequestException("请配置微信支付与公众号appId");
        }
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setAppId(appId);
        wxPayConfig.setMchId(mchId);
        wxPayConfig.setMchKey(mchKey);
        wxPayService.setConfig(wxPayConfig);

        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,0);
        if(ObjectUtil.isNull(orderInfo)) throw new ErrorRequestException("订单不存在");
        if(orderInfo.getPaid() == 1) throw new ErrorRequestException("该订单已支付");

        if(orderInfo.getPayPrice().doubleValue() <= 0) throw new ErrorRequestException("该支付无需支付");

        WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
        YxUser wechatUser = userService.getById(orderInfo.getUid());
        if(ObjectUtil.isNull(wechatUser)) throw new ErrorRequestException("用户错误");
        orderRequest.setTradeType("MWEB");
        orderRequest.setBody("商品购买");
        orderRequest.setOutTradeNo(orderId);
        BigDecimal bigDecimal = new BigDecimal(100);
        orderRequest.setTotalFee(bigDecimal.multiply(orderInfo.getPayPrice()).intValue());//元转成分
        orderRequest.setSpbillCreateIp("127.0.0.1");
        orderRequest.setNotifyUrl(apiUrl+"/api/wechat/notify");


        WxPayMwebOrderResult orderResult = wxPayService.createOrder(orderRequest);

        return orderResult;
    }

    /**
     * 小程序支付
     * @param orderId
     * @return
     * @throws WxPayException
     */
    @Override
    public WxPayMpOrderResult wxAppPay(String orderId) throws WxPayException {
        String apiUrl = systemConfigService.getData("api_url");
        if(StrUtil.isBlank(apiUrl)) throw new ErrorRequestException("请配置api地址");

        //读取redis配置
        String appId = RedisUtil.get("wxapp_appId");
        String mchId = RedisUtil.get("wxpay_mchId");
        String mchKey = RedisUtil.get("wxpay_mchKey");
        if(StrUtil.isBlank(appId) || StrUtil.isBlank(mchId) || StrUtil.isBlank(mchKey)){
            throw new ErrorRequestException("请配置微信支付与小程序appId");
        }
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setAppId(appId);
        wxPayConfig.setMchId(mchId);
        wxPayConfig.setMchKey(mchKey);
        wxPayService.setConfig(wxPayConfig);

        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,0);
        if(ObjectUtil.isNull(orderInfo)) throw new ErrorRequestException("订单不存在");
        if(orderInfo.getPaid() == 1) throw new ErrorRequestException("该订单已支付");

        if(orderInfo.getPayPrice().doubleValue() <= 0) throw new ErrorRequestException("该支付无需支付");

        WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
        YxWechatUser wechatUser = wechatUserService.getById(orderInfo.getUid());
        if(ObjectUtil.isNull(wechatUser)) throw new ErrorRequestException("用户错误");
        orderRequest.setTradeType("JSAPI");
        orderRequest.setOpenid(wechatUser.getRoutineOpenid());
        orderRequest.setBody("商品购买");
        orderRequest.setOutTradeNo(orderId);
        BigDecimal bigDecimal = new BigDecimal(100);
        orderRequest.setTotalFee(bigDecimal.multiply(orderInfo.getPayPrice()).intValue());//元转成分
        orderRequest.setSpbillCreateIp("127.0.0.1");
        orderRequest.setNotifyUrl(apiUrl+"/api/wechat/notify");


        WxPayMpOrderResult orderResult = wxPayService.createOrder(orderRequest);

        return orderResult;
    }

    /**
     * 微信支付
     * @param orderId
     */
    @Override
    public WxPayMpOrderResult wxPay(String orderId) throws WxPayException {
        String apiUrl = systemConfigService.getData("api_url");
        if(StrUtil.isBlank(apiUrl)) throw new ErrorRequestException("请配置api地址");

        //读取redis配置
        String appId = RedisUtil.get("wxpay_appId");
        String mchId = RedisUtil.get("wxpay_mchId");
        String mchKey = RedisUtil.get("wxpay_mchKey");
        if(StrUtil.isBlank(appId) || StrUtil.isBlank(mchId) || StrUtil.isBlank(mchKey)){
            throw new ErrorRequestException("请配置微信支付与公众号appId");
        }
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setAppId(appId);
        wxPayConfig.setMchId(mchId);
        wxPayConfig.setMchKey(mchKey);
        wxPayService.setConfig(wxPayConfig);

        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,0);
        if(ObjectUtil.isNull(orderInfo)) throw new ErrorRequestException("订单不存在");
        if(orderInfo.getPaid() == 1) throw new ErrorRequestException("该订单已支付");

        if(orderInfo.getPayPrice().doubleValue() <= 0) throw new ErrorRequestException("该支付无需支付");

        WxPayUnifiedOrderRequest orderRequest = new WxPayUnifiedOrderRequest();
        YxWechatUser wechatUser = wechatUserService.getById(orderInfo.getUid());
        if(ObjectUtil.isNull(wechatUser)) throw new ErrorRequestException("用户错误");
        orderRequest.setTradeType("JSAPI");
        orderRequest.setOpenid(wechatUser.getOpenid());
        orderRequest.setBody("商品购买");
        orderRequest.setOutTradeNo(orderId);
        BigDecimal bigDecimal = new BigDecimal(100);
        orderRequest.setTotalFee(bigDecimal.multiply(orderInfo.getPayPrice()).intValue());//元转成分
        orderRequest.setSpbillCreateIp("127.0.0.1");
        orderRequest.setNotifyUrl(apiUrl+"/api/wechat/notify");


        WxPayMpOrderResult orderResult = wxPayService.createOrder(orderRequest);

        return orderResult;

    }

    /**
     * 余额支付
     * @param orderId 订单号
     * @param uid 用户id
     */
    @Override
    public void yuePay(String orderId, int uid) {
        YxStoreOrderQueryVo orderInfo = getOrderInfo(orderId,uid);
        if(ObjectUtil.isNull(orderInfo)) throw new ErrorRequestException("订单不存在");

        if(orderInfo.getPaid() == 1) throw new ErrorRequestException("该订单已支付");

        YxUserQueryVo userInfo = userService.getYxUserById(uid);

        if(userInfo.getNowMoney().doubleValue() < orderInfo.getPayPrice().doubleValue()){
            throw new ErrorRequestException("余额不足");
        }

        userService.decPrice(uid,orderInfo.getPayPrice().doubleValue());

        YxUserBill userBill = new YxUserBill();
        userBill.setUid(uid);
        userBill.setTitle("购买商品");
        userBill.setLinkId(orderInfo.getId().toString());
        userBill.setCategory("now_money");
        userBill.setType("pay_product");
        userBill.setNumber(orderInfo.getPayPrice());
        userBill.setBalance(userInfo.getNowMoney());
        userBill.setMark("余额支付");
        userBill.setStatus(1);
        userBill.setPm(0);
        userBill.setAddTime(OrderUtil.getSecondTimestampTwo());
        billService.save(userBill);

        //支付成功后处理
        paySuccess(orderInfo.getOrderId(),"yue");

    }

    /**
     * 创建订单
     * @param uid uid
     * @param key key
     * @param param param
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public YxStoreOrder createOrder(int uid, String key, OrderParam param) {
        YxUserQueryVo userInfo = userService.getYxUserById(uid);
        if(ObjectUtil.isNull(userInfo)) throw new ErrorRequestException("用户不存在");

        CacheDTO cacheDTO = getCacheOrderInfo(uid,key);
        if(ObjectUtil.isNull(cacheDTO)){
            throw new ErrorRequestException("订单已过期,请刷新当前页面");
        }

        List<YxStoreCartQueryVo> cartInfo = cacheDTO.getCartInfo();
        Double totalPrice =  cacheDTO.getPriceGroup().getTotalPrice();
        Double payPrice = cacheDTO.getPriceGroup().getTotalPrice();
        Double payPostage = cacheDTO.getPriceGroup().getStorePostage();
        OtherDTO other = cacheDTO.getOther();
        YxUserAddressQueryVo userAddress = null;
        if(param.getShippingType() == 1){
            if(StrUtil.isEmpty(param.getAddressId())) throw new ErrorRequestException("请选择收货地址");
            userAddress = userAddressService.getYxUserAddressById(param.getAddressId());
            if(ObjectUtil.isNull(userAddress)) throw new ErrorRequestException("地址选择有误");
        }

        Integer totalNum = 0;
        Integer gainIntegral = 0;
        List<String> cartIds = new ArrayList<>();
        int combinationId = 0;
        int seckillId = 0;
        int bargainId = 0;

        for (YxStoreCartQueryVo cart : cartInfo) {
            combinationId = cart.getCombinationId();
            seckillId = cart.getSeckillId();
            bargainId = cart.getBargainId();
            cartIds.add(cart.getId().toString());
            totalNum += cart.getCartNum();
            //计算积分
            BigDecimal cartInfoGainIntegral = BigDecimal.ZERO;
            if(combinationId == 0 && seckillId == 0 && bargainId == 0){//拼团等活动不参与积分
                if(cart.getProductInfo().getGiveIntegral().intValue() > 0){
                    cartInfoGainIntegral = NumberUtil.mul(cart.getCartNum(),cart.
                            getProductInfo().getGiveIntegral());
                }
                gainIntegral = NumberUtil.add(gainIntegral,cartInfoGainIntegral).intValue();
            }

        }


        //todo 门店等二期

        if(param.getShippingType() == 1){
            payPrice = NumberUtil.add(payPrice,payPostage);
        }

        //优惠券
        int couponId = 0;
        if(ObjectUtil.isNotEmpty(param.getCouponId())){
            couponId = param.getCouponId().intValue();
        }

        int useIntegral = param.getUseIntegral().intValue();

        boolean deduction = false;//拼团等
        //拼团等不参与抵扣
        if(combinationId > 0 || seckillId > 0 || bargainId > 0) deduction = true;
        if(deduction){
            couponId = 0;
            useIntegral = 0;
        }
        double couponPrice = 0; //优惠券金额
        if(couponId > 0){//使用优惠券
            YxStoreCouponUser couponUser = couponUserService.getCoupon(couponId,uid);
            if(ObjectUtil.isNull(couponUser)) throw new ErrorRequestException("使用优惠劵失败");

            if(couponUser.getUseMinPrice().doubleValue() > payPrice){
                throw new ErrorRequestException("不满足优惠劵的使用条件");
            }
            payPrice = NumberUtil.sub(payPrice,couponUser.getCouponPrice()).doubleValue();

            couponUserService.useCoupon(couponId);//更新优惠券状态

            couponPrice = couponUser.getCouponPrice().doubleValue();

        }
        // 积分抵扣
        double deductionPrice = 0; //抵扣金额
        double usedIntegral = 0; //使用的积分
        if(useIntegral > 0 && userInfo.getIntegral().doubleValue() > 0){
            deductionPrice = NumberUtil.mul(userInfo.getIntegral(),
                    Double.valueOf(cacheDTO.getOther().getIntegralRatio())).doubleValue();
            if(deductionPrice < payPrice){
                payPrice = NumberUtil.sub(payPrice.doubleValue(),deductionPrice);
                usedIntegral = userInfo.getIntegral().doubleValue();
                YxUser yxUser = new YxUser();
                yxUser.setIntegral(BigDecimal.ZERO);
                yxUser.setUid(uid);
                userService.updateById(yxUser);
            }else{
                deductionPrice = payPrice;
                usedIntegral = NumberUtil.div(payPrice,
                        Double.valueOf(cacheDTO.getOther().getIntegralRatio()));
                userService.decIntegral(uid,usedIntegral);
                payPrice = 0d;

            }

            //积分流水
            YxUserBill userBill = new YxUserBill();
            userBill.setUid(uid);
            userBill.setTitle("积分抵扣");
            userBill.setLinkId(key);
            userBill.setCategory("integral");
            userBill.setType("deduction");
            userBill.setNumber(BigDecimal.valueOf(usedIntegral));
            userBill.setBalance(userInfo.getIntegral());
            userBill.setMark("购买商品使用");
            userBill.setStatus(1);
            userBill.setPm(0);
            userBill.setAddTime(OrderUtil.getSecondTimestampTwo());
            billService.save(userBill);

        }

        if(payPrice <= 0) payPrice = 0d;

        //生成分布式唯一值
        String orderSn = IdUtil.getSnowflake(0,0).nextIdStr();
        //组合数据
        YxStoreOrder storeOrder = new YxStoreOrder();
        storeOrder.setUid(uid);
        storeOrder.setOrderId(orderSn);
        storeOrder.setRealName(userAddress.getRealName());
        storeOrder.setUserPhone(userAddress.getPhone());
        storeOrder.setUserAddress(userAddress.getProvince()+" "+userAddress.getCity()+
                " "+userAddress.getDistrict()+" "+userAddress.getDetail());
        storeOrder.setCartId(StrUtil.join(",",cartIds));
        storeOrder.setTotalNum(totalNum);
        storeOrder.setTotalPrice(BigDecimal.valueOf(totalPrice));
        storeOrder.setTotalPostage(BigDecimal.valueOf(payPostage));
        storeOrder.setCouponId(couponId);
        storeOrder.setCouponPrice(BigDecimal.valueOf(couponPrice));
        storeOrder.setPayPrice(BigDecimal.valueOf(payPrice));
        storeOrder.setPayPostage(BigDecimal.valueOf(payPostage));
        storeOrder.setDeductionPrice(BigDecimal.valueOf(deductionPrice));
        storeOrder.setPaid(0);
        storeOrder.setPayType(param.getPayType());
        storeOrder.setUseIntegral(BigDecimal.valueOf(usedIntegral));
        storeOrder.setGainIntegral(BigDecimal.valueOf(gainIntegral));
        storeOrder.setMark(param.getMark());
        storeOrder.setCombinationId(combinationId);
        storeOrder.setPinkId(param.getPinkId());
        storeOrder.setSeckillId(seckillId);
        storeOrder.setBargainId(bargainId);
        storeOrder.setCost(BigDecimal.valueOf(cacheDTO.getPriceGroup().getCostPrice()));
        storeOrder.setIsChannel(param.getIsChannel());
        storeOrder.setAddTime(OrderUtil.getSecondTimestampTwo());
        storeOrder.setUnique(key);
        storeOrder.setShippingType(param.getShippingType());

        boolean res = save(storeOrder);
        if(!res) throw new ErrorRequestException("订单生成失败");

        //减库存加销量
        for (YxStoreCartQueryVo cart : cartInfo) {
            if(combinationId > 0){
                combinationService.decStockIncSales(cart.getCartNum(),combinationId);
            }else if(seckillId > 0){
                storeSeckillService.decStockIncSales(cart.getCartNum(),seckillId);
            }else if(bargainId > 0){
                storeBargainService.decStockIncSales(cart.getCartNum(),bargainId);
            } else {
                productService.decProductStock(cart.getCartNum(),cart.getProductId(),
                        cart.getProductAttrUnique());
            }

        }

        //保存购物车商品信息
        orderCartInfoService.saveCartInfo(storeOrder.getId(),cartInfo);

        //购物车状态修改
        QueryWrapper<YxStoreCart> wrapper = new QueryWrapper<>();
        wrapper.in("id",cartIds);
        YxStoreCart cartObj = new YxStoreCart();
        cartObj.setIsPay(1);
        storeCartMapper.update(cartObj,wrapper);

        //删除缓存
        delCacheOrderInfo(uid,key);

        //增加状态
        orderStatusService.create(storeOrder.getId(),"cache_key_create_order","订单生成");


        //使用MQ延时消息
        //mqProducer.sendMsg("yshop-topic",storeOrder.getId().toString());
        //log.info("投递延时订单id： [{}]：", storeOrder.getId());

        return storeOrder;
    }

    /**
     * 计算价格
     * @param key
     * @param couponId
     * @param useIntegral
     * @param shippingType
     * @return
     */
    @Override
    public ComputeDTO computedOrder(int uid, String key, int couponId,
                                    int useIntegral, int shippingType) {
        YxUserQueryVo userInfo = userService.getYxUserById(uid);
        if(ObjectUtil.isNull(userInfo)) throw new ErrorRequestException("用户不存在");
        CacheDTO cacheDTO = getCacheOrderInfo(uid,key);
        if(ObjectUtil.isNull(cacheDTO)){
            throw new ErrorRequestException("订单已过期,请刷新当前页面");
        }
        ComputeDTO computeDTO = new ComputeDTO();
        computeDTO.setTotalPrice(cacheDTO.getPriceGroup().getTotalPrice());
        Double payPrice = cacheDTO.getPriceGroup().getTotalPrice();
        Double payPostage = cacheDTO.getPriceGroup().getStorePostage();

        boolean deduction = false;//拼团秒杀砍价等
        int combinationId = 0;
        int seckillId = 0;
        List<YxStoreCartQueryVo> cartInfo = cacheDTO.getCartInfo();
        for (YxStoreCartQueryVo cart : cartInfo) {
            combinationId = cart.getCombinationId();
            seckillId = cart.getSeckillId();
        }
        //拼团等不参与抵扣
        if(combinationId > 0 || seckillId > 0) deduction = true;


        if(deduction){
            couponId = 0;
            useIntegral = 0;
        }
        double couponPrice = 0;
        if(couponId > 0){//使用优惠券
            YxStoreCouponUser couponUser = couponUserService.getCoupon(couponId,uid);
            if(ObjectUtil.isNull(couponUser)) throw new ErrorRequestException("使用优惠劵失败");

            if(couponUser.getUseMinPrice().doubleValue() > payPrice){
                throw new ErrorRequestException("不满足优惠劵的使用条件");
            }
            payPrice = NumberUtil.sub(payPrice,couponUser.getCouponPrice()).doubleValue();

            couponPrice = couponUser.getCouponPrice().doubleValue();

        }

        // 积分抵扣
        double deductionPrice = 0;
        if(useIntegral > 0 && userInfo.getIntegral().doubleValue() > 0){
            deductionPrice = NumberUtil.mul(userInfo.getIntegral(),
                    Double.valueOf(cacheDTO.getOther().getIntegralRatio())).doubleValue();
            if(deductionPrice < payPrice){
                payPrice = NumberUtil.sub(payPrice.doubleValue(),deductionPrice);
            }else{
                deductionPrice = payPrice;
                payPrice = 0d;
            }

        }

        if(payPrice <= 0) payPrice = 0d;

        computeDTO.setPayPrice(payPrice);
        computeDTO.setPayPostage(payPostage);
        computeDTO.setCouponPrice(couponPrice);
        computeDTO.setDeductionPrice(deductionPrice);

        return computeDTO;
    }

    /**
     * 订单信息
     * @param unique
     * @param uid
     * @return
     */
    @Override
    public YxStoreOrderQueryVo getOrderInfo(String unique,int uid) {
        QueryWrapper<YxStoreOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("is_del",0).and(
                i->i.eq("order_id",unique).or().eq("`unique`",unique));
        if(uid > 0) wrapper.eq("uid",uid);

        return orderMap.toDto(yxStoreOrderMapper.selectOne(wrapper));
    }

    @Override
    public CacheDTO getCacheOrderInfo(int uid, String key) {

        return (CacheDTO)redisService.getObj("user_order_"+uid+key);
    }

    @Override
    public void delCacheOrderInfo(int uid, String key) {
        redisService.delete("user_order_"+uid+key);
    }

    /**
     * 缓存订单
     * @param uid uid
     * @param cartInfo cartInfo
     * @param priceGroup priceGroup
     * @param other other
     * @return
     */
    @Override
    public String cacheOrderInfo(int uid, List<YxStoreCartQueryVo> cartInfo, PriceGroupDTO priceGroup, OtherDTO other) {
        String key = IdUtil.simpleUUID();
        CacheDTO cacheDTO = new CacheDTO();
        cacheDTO.setCartInfo(cartInfo);
        cacheDTO.setPriceGroup(priceGroup);
        cacheDTO.setOther(other);
        redisService.saveCode("user_order_"+uid+key,cacheDTO,600L);
        return key;
    }

    /**
     * 获取订单价格
     * @param cartInfo
     * @return
     */
    @Override
    public PriceGroupDTO getOrderPriceGroup(List<YxStoreCartQueryVo> cartInfo) {

        String storePostageStr = systemConfigService.getData("store_postage");//邮费基础价
        Double storePostage = 0d;
        if(StrUtil.isNotEmpty(storePostageStr)) storePostage = Double.valueOf(storePostageStr);

        String storeFreePostageStr = systemConfigService.getData("store_free_postage");//满额包邮
        Double storeFreePostage = 0d;
        if(StrUtil.isNotEmpty(storeFreePostageStr)) storeFreePostage = Double.valueOf(storeFreePostageStr);

        Double totalPrice = getOrderSumPrice(cartInfo, "truePrice");//获取订单总金额
        Double costPrice = getOrderSumPrice(cartInfo, "costPrice");//获取订单成本价
        Double vipPrice = getOrderSumPrice(cartInfo, "vipTruePrice");//获取订单会员优惠金额

        if(storeFreePostage == 0){//包邮
            storePostage = 0d;
        }else{
            for (YxStoreCartQueryVo storeCart : cartInfo) {
                if(storeCart.getProductInfo().getIsPostage() == 0){//不包邮
                    storePostage = NumberUtil.add(storePostage
                            ,storeCart.getProductInfo().getPostage()).doubleValue();
                }
            }
            //如果总价大于等于满额包邮 邮费等于0
            if (storeFreePostage <= totalPrice) storePostage = 0d;
        }

        PriceGroupDTO priceGroupDTO = new PriceGroupDTO();
        priceGroupDTO.setStorePostage(storePostage);
        priceGroupDTO.setStoreFreePostage(storeFreePostage);
        priceGroupDTO.setTotalPrice(totalPrice);
        priceGroupDTO.setCostPrice(costPrice);
        priceGroupDTO.setVipPrice(vipPrice);

        return priceGroupDTO;
    }

    /**
     * 获取某字段价格
     * @param cartInfo
     * @param key
     * @return
     */
    @Override
    public Double getOrderSumPrice(List<YxStoreCartQueryVo> cartInfo, String key) {
        BigDecimal sumPrice = BigDecimal.ZERO;

        if(key.equals("truePrice")){
            for (YxStoreCartQueryVo storeCart : cartInfo) {
                sumPrice = NumberUtil.add(sumPrice,NumberUtil.mul(storeCart.getCartNum(),storeCart.getTruePrice()));
            }
        }else if(key.equals("costPrice")){
            for (YxStoreCartQueryVo storeCart : cartInfo) {
                sumPrice = NumberUtil.add(sumPrice,
                        NumberUtil.mul(storeCart.getCartNum(),storeCart.getCostPrice()));
            }
        }else if(key.equals("vipTruePrice")){
            for (YxStoreCartQueryVo storeCart : cartInfo) {
                sumPrice = NumberUtil.add(sumPrice,
                        NumberUtil.mul(storeCart.getCartNum(),storeCart.getVipTruePrice()));
            }
        }

        //System.out.println("sumPrice:"+sumPrice);
        return sumPrice.doubleValue();
    }

    @Override
    public YxStoreOrderQueryVo getYxStoreOrderById(Serializable id) throws Exception{
        return yxStoreOrderMapper.getYxStoreOrderById(id);
    }

    @Override
    public Paging<YxStoreOrderQueryVo> getYxStoreOrderPageList(YxStoreOrderQueryParam yxStoreOrderQueryParam) throws Exception{
        Page page = setPageParam(yxStoreOrderQueryParam,OrderItem.desc("create_time"));
        IPage<YxStoreOrderQueryVo> iPage = yxStoreOrderMapper.getYxStoreOrderPageList(page,yxStoreOrderQueryParam);
        return new Paging(iPage);
    }

}
