package co.yixiang.modules.shop.service.impl;

import co.yixiang.modules.shop.entity.YxStoreCoupon;
import co.yixiang.modules.shop.mapper.YxStoreCouponMapper;
import co.yixiang.modules.shop.service.YxStoreCouponService;
import co.yixiang.modules.shop.web.param.YxStoreCouponQueryParam;
import co.yixiang.modules.shop.web.vo.YxStoreCouponQueryVo;
import co.yixiang.common.service.impl.BaseServiceImpl;
import co.yixiang.common.web.vo.Paging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.Serializable;


/**
 * <p>
 * 优惠券表 服务实现类
 * </p>
 *
 * @author hupeng
 * @since 2019-10-27
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class YxStoreCouponServiceImpl extends BaseServiceImpl<YxStoreCouponMapper, YxStoreCoupon> implements YxStoreCouponService {

    @Autowired
    private YxStoreCouponMapper yxStoreCouponMapper;

    @Override
    public YxStoreCouponQueryVo getYxStoreCouponById(Serializable id){
        return yxStoreCouponMapper.getYxStoreCouponById(id);
    }

    @Override
    public Paging<YxStoreCouponQueryVo> getYxStoreCouponPageList(YxStoreCouponQueryParam yxStoreCouponQueryParam) throws Exception{
        Page page = setPageParam(yxStoreCouponQueryParam,OrderItem.desc("create_time"));
        IPage<YxStoreCouponQueryVo> iPage = yxStoreCouponMapper.getYxStoreCouponPageList(page,yxStoreCouponQueryParam);
        return new Paging(iPage);
    }

}
