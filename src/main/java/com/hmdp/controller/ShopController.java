package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    private IShopService shopService;

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") long id){
        return shopService.queryById(id);
    }

    @PostMapping
    public Result saveShop(@RequestBody Shop shop){
        return shopService.saveShop(shop);
    }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop){
        return shopService.update(shop);
    }

    /**
     * 按类型查询商户。
     *
     * 示例：
     * /shop/of/type?typeId=1&current=1
     * /shop/of/type?typeId=1&current=1&x=120.149&y=30.334
     */
    @GetMapping("/of/type")
    public Result queryShopByType(@RequestParam("typeId") Integer typeId,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current,
                                  @RequestParam(value = "x", required = false) Double x,
                                  @RequestParam(value = "y", required = false) Double y
                                ){
        return shopService.queryShopByType(typeId, current, x, y);
    }


    /**
     * 按名称搜索商户。
     */
    @GetMapping("/of/name")
    public Result queryShopByName(@RequestParam(value = "name", required = false ) String name,
                                  @RequestParam(value = "current",defaultValue = "1") Integer current){
        Page<Shop> shopPage = shopService
                .query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(shopPage.getRecords());
    }
}
