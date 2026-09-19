package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
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
}
