package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result saveShop(Shop shop);

    /**
     * 按商户类型分页查询。
     * 有经纬度时按距离查询附近商户；
     * 未传经纬度时按数据库普通分页查询。
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
