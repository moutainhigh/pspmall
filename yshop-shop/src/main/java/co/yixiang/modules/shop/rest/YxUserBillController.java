package co.yixiang.modules.shop.rest;

import co.yixiang.aop.log.Log;
import co.yixiang.modules.shop.service.YxUserBillService;
import co.yixiang.modules.shop.service.dto.YxUserBillQueryCriteria;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
* @author hupeng
* @date 2019-11-06
*/
@Api(tags = "用户账单管理")
@RestController
@RequestMapping("api")
public class YxUserBillController {

    @Autowired
    private YxUserBillService yxUserBillService;

    @Log("查询")
    @ApiOperation(value = "查询")
    @GetMapping(value = "/yxUserBill")
    @PreAuthorize("hasAnyRole('ADMIN','YXUSERBILL_ALL','YXUSERBILL_SELECT')")
    public ResponseEntity getYxUserBills(YxUserBillQueryCriteria criteria, Pageable pageable){
        return new ResponseEntity(yxUserBillService.queryAll(criteria,pageable),HttpStatus.OK);
    }






}