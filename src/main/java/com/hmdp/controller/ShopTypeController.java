package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService shopTypeService;

    @GetMapping("/list")
    public Result queryTypeList(){
        return shopTypeService.querySort();
    }

}
