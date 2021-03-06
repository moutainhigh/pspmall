package co.yixiang.modules.user.service.impl;

import co.yixiang.modules.user.entity.YxUserBill;
import co.yixiang.modules.user.mapper.YxUserBillMapper;
import co.yixiang.modules.user.mapping.BiillMap;
import co.yixiang.modules.user.service.YxUserBillService;
import co.yixiang.modules.user.web.dto.BillDTO;
import co.yixiang.modules.user.web.dto.BillOrderDTO;
import co.yixiang.modules.user.web.dto.BillOrderRecordDTO;
import co.yixiang.modules.user.web.param.YxUserBillQueryParam;
import co.yixiang.modules.user.web.vo.YxUserBillQueryVo;
import co.yixiang.common.service.impl.BaseServiceImpl;
import co.yixiang.common.web.vo.Paging;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * <p>
 * 用户账单表 服务实现类
 * </p>
 *
 * @author hupeng
 * @since 2019-10-27
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class YxUserBillServiceImpl extends BaseServiceImpl<YxUserBillMapper, YxUserBill> implements YxUserBillService {

    @Autowired
    private YxUserBillMapper yxUserBillMapper;

    @Autowired
    private BiillMap biillMap;


    /**
     * 签到了多少次
     * @param uid
     * @return
     */
    @Override
    public int cumulativeAttendance(int uid) {
        QueryWrapper<YxUserBill> wrapper = new QueryWrapper<>();
        wrapper.eq("uid",uid).eq("category","integral")
                .eq("type","sign").eq("pm",1);
        return yxUserBillMapper.selectCount(wrapper);
    }

    @Override
    public Map<String, Object> spreadOrder(int uid, int page, int limit) {
        QueryWrapper<YxUserBill> wrapper = new QueryWrapper<>();
        wrapper.in("uid",uid).eq("type","brokerage")
                .eq("category","now_money").orderByDesc("time")
                .groupBy("time");
        Page<YxUserBill> pageModel = new Page<>(page, limit);
        List<String> list = yxUserBillMapper.getBillOrderList(wrapper,pageModel);


//        QueryWrapper<YxUserBill> wrapperT = new QueryWrapper<>();
//        wrapperT.in("uid",uid).eq("type","brokerage")
//                .eq("category","now_money");

        int count = (int)pageModel.getTotal();
        List<BillOrderDTO> listT = new ArrayList<>();
        for (String str : list) {
            BillOrderDTO billOrderDTO = new BillOrderDTO();
            List<BillOrderRecordDTO> orderRecordDTOS = yxUserBillMapper
                    .getBillOrderRList(str,uid);
            billOrderDTO.setChild(orderRecordDTOS);
            billOrderDTO.setCount(orderRecordDTOS.size());
            billOrderDTO.setTime(str);

            listT.add(billOrderDTO);
        }

        Map<String,Object> map = new LinkedHashMap<>();
        map.put("list",listT);
        map.put("count",count);

        return map;
    }

    @Override
    public List<BillDTO> getUserBillList(int page, int limit, int uid, int type) {
        QueryWrapper<YxUserBill> wrapper = new QueryWrapper<>();
        wrapper.eq("uid",uid).orderByDesc("add_time").groupBy("time");
        switch (type){
            case 0:
                wrapper.eq("category","now_money");
                String str = "recharge,brokerage,pay_product,system_add,pay_product_refund,system_sub";
                wrapper.in("type",str.split(","));
                break;
            case 1:
                wrapper.eq("category","now_money");
                wrapper.eq("type","pay_product");
                break;
            case 2:
                wrapper.eq("category","now_money");
                wrapper.eq("type","recharge");
                break;
            case 3:
                wrapper.eq("category","now_money");
                wrapper.eq("type","brokerage");
                break;
            case 4:
                wrapper.eq("category","now_money");
                wrapper.eq("type","extract");
                break;
            case 5:
                wrapper.eq("category","integral");
                wrapper.eq("type","sign");
                break;
        }
        Page<YxUserBill> pageModel = new Page<>(page, limit);
        List<BillDTO> billDTOList = yxUserBillMapper.getBillList(wrapper,pageModel);
        for (BillDTO billDTO : billDTOList) {
            QueryWrapper<YxUserBill> wrapperT = new QueryWrapper<>();
            wrapperT.in("id",billDTO.getIds().split(","));
            billDTO.setList(yxUserBillMapper.getUserBillList(wrapperT));

        }

        return billDTOList;
    }

    @Override
    public double getBrokerage(int uid) {
        return yxUserBillMapper.sumPrice(uid);
    }

    @Override
    public double yesterdayCommissionSum(int uid) {
        return yxUserBillMapper.sumYesterdayPrice(uid);
    }

    @Override
    public List<YxUserBillQueryVo> userBillList(int uid, int page,
                                                int limit, String category) {
        QueryWrapper<YxUserBill> wrapper = new QueryWrapper<>();
        wrapper.eq("status",1).eq("uid",uid)
                .eq("category",category).orderByDesc("add_time");
        Page<YxUserBill> pageModel = new Page<>(page, limit);

        IPage<YxUserBill> pageList = yxUserBillMapper.selectPage(pageModel,wrapper);
        return biillMap.toDto(pageList.getRecords());
    }

    @Override
    public YxUserBillQueryVo getYxUserBillById(Serializable id) throws Exception{
        return yxUserBillMapper.getYxUserBillById(id);
    }

    @Override
    public Paging<YxUserBillQueryVo> getYxUserBillPageList(YxUserBillQueryParam yxUserBillQueryParam) throws Exception{
        Page page = setPageParam(yxUserBillQueryParam,OrderItem.desc("add_time"));
        IPage<YxUserBillQueryVo> iPage = yxUserBillMapper.getYxUserBillPageList(page,yxUserBillQueryParam);
        return new Paging(iPage);
    }

}
