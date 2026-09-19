package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {

    Result querySort();
}
