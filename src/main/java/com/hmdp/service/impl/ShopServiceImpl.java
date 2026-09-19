package com.hmdp.service.impl;

import cn.hutool.bloomfilter.BloomFilter;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.ShopCacheProperties;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.ShopBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


/*
 * 商户业务层只负责决定走哪中缓存策略
 *
 * 普通商品: Bloom -> 空值缓存 + 随机TTL防止缓存雪崩
 * 热点商品：Bloom -> 逻辑过期 + 加锁异步重建
 *
 * CacheClinet 不知道Shop业务
 * ShopServiceImpl 不重复写JSON、锁、TTL等通用逻辑。
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    private final ShopCacheProperties shopCacheProperties;
    private final ShopBloomFilter shopBloomFilter;

    public ShopServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            CacheClient cacheClient,
            ShopBloomFilter shopBloomFilter,
            ShopCacheProperties shopCacheProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
        this.shopBloomFilter = shopBloomFilter;
        this.shopCacheProperties = shopCacheProperties;
    }

    @Override
    public Result queryById(Long id) {
        if (id == null || id <= 0L){
            return Result.fail("商品id不合法");
        }
        /*
         * Bloom fasle: 一定不存在
         * Bloom true: 只是可能存在，不能直接返回商户；
         * 扔要走 Redis 与MySQL;
         */
        if (!shopBloomFilter.mightContain(id)){
            return Result.fail("商户不存在");
        }
        Shop shop;

        if (shopCacheProperties.isHotShop(id)){
            /*
             * 热点商品
             * RedisData + 逻辑过期 + 安全锁 + 异步重建
             */

             shop = cacheClient.queryWithLogicalExpire(
                     RedisConstants.CACHE_SHOP_KEY,
                     id,
                     Shop.class,
                     this::getById,
                     RedisConstants.CACHE_SHOP_LOGICAL_TTL,
                     TimeUnit.MINUTES
             );
        }else {
            /*
             * 普通商品
             * JSON缓存 + 空值缓存 + 随机TTL
             */
            shop = cacheClient.queryWithPassThrough(
                    RedisConstants.CACHE_SHOP_KEY,
                    id,
                    Shop.class,
                    this::getById,
                    RedisConstants.CACHE_SHOP_TTL,
                    RedisConstants.CACHE_SHOP_TTL_RANDOM,
                    TimeUnit.MINUTES
            );
        }
        if (shop == null){
            return Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }


    @Override
    @Transactional
    public Result saveShop(Shop shop) {
        if (shop == null || shop.getName() == null || shop.getName().trim().isEmpty()){
            return Result.fail("商品名称不能为kong");
        }

        boolean saved = save(shop);

        //判断是否成功
        if (!saved || shop.getId() == null){
            return Result.fail("新增商户失败");
        }

        final Long shopId = shop.getId();
        final Shop saveShop = shop;


        /*
         *
         * 必须在MySQL commit成功后再写Bloom
         * 否则MySQL 回滚了，Bloom却记录了一个不存在的
         * 虽然不会造成数据错误，但是造成了无意义的假阳性
         *
         */
        runAfterCommit(new Runnable(){
            @Override
            public void run() {
                shopBloomFilter.add(shopId);

                // 判断是否是热门商品，如果是，就设置逻辑过期
                if (shopCacheProperties.isHotShop(shopId)){
                    cacheClient.setWithLogicalExpire(
                            RedisConstants.CACHE_SHOP_KEY + shopId,
                            saveShop,
                            RedisConstants.CACHE_SHOP_LOGICAL_TTL,
                            TimeUnit.MINUTES
                    );
                }
            }
        });


        return Result.ok(shopId);
    }

    /*
     * Cache Aside;
     * 先更新MySQL，提交成功后删除Redis，
     * 下一次查询中会从MySQL读取最新值，并写回缓存。
     */

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop == null || shop.getId() == null){
            return Result.fail("商品id不能为空");
        }

        boolean updated = updateById(shop);

        if (!updated){
            return Result.fail("商品不存在");
        }

        final long shopId = shop.getId();


        runAfterCommit(new Runnable() {
            @Override
            public void run() {
                stringRedisTemplate.delete(
                        RedisConstants.CACHE_SHOP_KEY + shopId
                );

            }
        });

        return  Result.ok();

    }

    private void runAfterCommit(final Runnable action){
        // 如果当前有事务同步机制，就等事务提交完成后action；如果没有，就立即执行
        if (!TransactionSynchronizationManager.isSynchronizationActive()){
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
        );



    }




}
