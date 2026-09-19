package com.hmdp.config;


//创建Bloom初始化与热点预热类
/*
 * Spring Boot 启动完成后执行一次
 *
 *  1. 创建Bloom Filter
 *  2. 将Mysql 所有商户ID 写入 Bloom
 *  3. 预热配置中的热点商品
 *
 *  注意：
 *  Bloom 未构建完成前，不能启用 “Bloom false直接返回不存在的逻辑”
 */


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.ShopBloomFilter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class ShopCacheInitializer {
    private final ShopCacheProperties shopCacheProperties; // 热点类
    private final ShopBloomFilter shopBloomFilter;   //BloomFilter
    private final CacheClient cacheClient;
    private final ShopMapper shopMapper;

    public ShopCacheInitializer(
            ShopMapper shopMapper,
            ShopBloomFilter shopBloomFilter,
            CacheClient cacheClient,
            ShopCacheProperties shopCacheProperties
    ) {
        this.shopMapper = shopMapper;
        this.shopBloomFilter = shopBloomFilter;
        this.cacheClient = cacheClient;
        this.shopCacheProperties = shopCacheProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeShopCache(){
        // 1.创建Bloom
        shopBloomFilter.createIfAbsent();

        // 2.读取MySql 所有商户ID，注意这里是Object对象
        List<Object> rawIds = shopMapper.selectObjs(
                new QueryWrapper<Shop>().select("id")
        );

        // 转换格式
        List<Long> shopIds = new ArrayList<Long>();
        for (Object rawId: rawIds){
            shopIds.add( ((Number)rawId).longValue());
        }

        // 3. 批量写入Bloom;重复写入没有问题
        shopBloomFilter.addAll(shopIds);

        // 4. 预热热点商品的逻辑过期缓存
        for (Long hotShopId : shopCacheProperties.getHotShopIds()){
            Shop shop = shopMapper.selectById(hotShopId);

            cacheClient.setWithLogicalExpire(
                    RedisConstants.CACHE_SHOP_KEY + hotShopId,
                    shop,
                    RedisConstants.CACHE_SHOP_LOGICAL_TTL,
                    TimeUnit.MINUTES
            );
        }
    }





}
