package co.yixiang.modules.user.service;

import co.yixiang.common.service.BaseService;
import co.yixiang.common.web.vo.Paging;
import co.yixiang.modules.user.entity.YxUserBill;
import co.yixiang.modules.user.web.dto.BillDTO;
import co.yixiang.modules.user.web.param.YxUserBillQueryParam;
import co.yixiang.modules.user.web.vo.YxUserBillQueryVo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户账单表 服务类
 * </p>
 *
 * @author hupeng
 * @since 2019-10-27
 */
public interface YxUserBillService extends BaseService<YxUserBill> {

    int cumulativeAttendance(int uid);

    Map<String,Object> spreadOrder(int uid,int page,int limit);

    List<BillDTO> getUserBillList(int page, int limit, int uid, int type);

    double getBrokerage(int uid);

    double yesterdayCommissionSum(int uid);

    List<YxUserBillQueryVo> userBillList(int uid, int page, int limit, String category);

    /**
     * 根据ID获取查询对象
     * @param id
     * @return
     */
    YxUserBillQueryVo getYxUserBillById(Serializable id) throws Exception;

    /**
     * 获取分页对象
     * @param yxUserBillQueryParam
     * @return
     */
    Paging<YxUserBillQueryVo> getYxUserBillPageList(YxUserBillQueryParam yxUserBillQueryParam) throws Exception;

}
