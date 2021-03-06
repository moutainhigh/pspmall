package co.yixiang.modules.shop.service;

import co.yixiang.modules.shop.entity.YxStoreCouponUser;
import co.yixiang.common.service.BaseService;
import co.yixiang.modules.shop.web.param.YxStoreCouponUserQueryParam;
import co.yixiang.modules.shop.web.vo.YxStoreCouponUserQueryVo;
import co.yixiang.common.web.vo.Paging;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 优惠券发放记录表 服务类
 * </p>
 *
 * @author hupeng
 * @since 2019-10-27
 */
public interface YxStoreCouponUserService extends BaseService<YxStoreCouponUser> {

    void useCoupon(int id);

    YxStoreCouponUser getCoupon(int id,int uid);

    List<YxStoreCouponUser> beUsableCouponList(int uid,double price);

    YxStoreCouponUser beUsableCoupon(int uid,double price);

    void checkInvalidCoupon(int uid);

    List<YxStoreCouponUserQueryVo > getUserCoupon(int uid,int type);

    void addUserCoupon(int uid,int cid);

    /**
     * 根据ID获取查询对象
     * @param id
     * @return
     */
    YxStoreCouponUserQueryVo getYxStoreCouponUserById(Serializable id) throws Exception;

    /**
     * 获取分页对象
     * @param yxStoreCouponUserQueryParam
     * @return
     */
    Paging<YxStoreCouponUserQueryVo> getYxStoreCouponUserPageList(YxStoreCouponUserQueryParam yxStoreCouponUserQueryParam) throws Exception;

}
