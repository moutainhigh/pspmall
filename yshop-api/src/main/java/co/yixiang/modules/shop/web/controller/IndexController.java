package co.yixiang.modules.shop.web.controller;

import co.yixiang.annotation.AnonymousAccess;
import co.yixiang.common.api.ApiResult;
import co.yixiang.modules.shop.service.YxStoreProductService;
import co.yixiang.modules.shop.service.YxSystemConfigService;
import co.yixiang.modules.shop.service.YxSystemGroupDataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName IndexController
 * @Author hupeng <610796224@qq.com>
 * @Date 2019/10/19
 **/

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Api(value = "首页模块", tags = "首页模块", description = "首页模块")
public class IndexController {

    private final YxSystemGroupDataService systemGroupDataService;
    private final YxSystemConfigService systemConfigService;
    private final YxStoreProductService storeProductService;



    @AnonymousAccess
    @GetMapping("/index")
    @ApiOperation(value = "首页数据",notes = "首页数据")
    public ApiResult<Map<String,Object>> index(){


        Map<String,Object> map = new LinkedHashMap<>();
        //banner
        map.put("banner",systemGroupDataService.getDatas("routine_home_banner"));
        //首页按钮
        map.put("menus",systemGroupDataService.getDatas("routine_home_menus"));
        //首页活动区域图片
        map.put("activity",systemGroupDataService.getDatas("routine_home_activity"));


        //精品推荐
        map.put("bastList",storeProductService.getList(1,6,1));
        //首发新品
        map.put("firstList",storeProductService.getList(1,6,2));
        //促销单品
        map.put("benefit",storeProductService.getList(1,3,3));
        //热门榜单
        map.put("likeInfo",storeProductService.getList(1,3,4));

        //滚动
        map.put("roll",systemGroupDataService.getDatas("routine_home_roll_news"));




        return ApiResult.ok(map);
    }

    @AnonymousAccess
    @GetMapping("/search/keyword")
    @ApiOperation(value = "热门搜索关键字获取",notes = "热门搜索关键字获取")
    public ApiResult<List<String>> search(){
        List<Map<String,Object>> list = systemGroupDataService.getDatas("routine_hot_search");
        List<String>  stringList = new ArrayList<>();
        for (Map<String,Object> map : list) {
            stringList.add(map.get("title").toString());
        }
        //System.out.println(stringList);
        return ApiResult.ok(stringList);
    }

    @AnonymousAccess
    @PostMapping("/image_base64")
    @ApiOperation(value = "获取图片base64",notes = "获取图片base64")
    public ApiResult<List<String>> imageBase64(){

        //Map<String,Object> map = new LinkedHashMap<>();
        return ApiResult.ok(null);
    }



}
