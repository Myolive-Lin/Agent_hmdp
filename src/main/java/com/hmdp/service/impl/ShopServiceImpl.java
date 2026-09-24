package com.hmdp.service.impl;

import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.ShopCacheProperties;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.ShopBloomFilter;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.*;
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

    /**
     * 根据 id 查询商户详情。
     */
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


    /**
     * 新增商户。
     */
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

    /**
     * 按类型查询商户。
     *
     * 示例：
     * /shop/of/type?typeId=1&current=1
     * /shop/of/type?typeId=1&current=1&x=120.149&y=30.334
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (typeId == null || current == null || current < 1) {
            return Result.fail("请求参数错误");
        }

        /*
         * 未上传用户位置：
         * 无法计算距离，使用 MySQL 的普通分页查询。
         */
        if (x == null || y == null) {
            Page<Shop> shopPage = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

            return Result.ok(shopPage.getRecords());
        }
        /*
         * 上传了位置：
         * 到 Redis GEO 中按距离查询店铺 ID。
         */
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;

        /*
         * 数据格式
           results
            └── content
                ├── GeoResult
                │   ├── content
                │   │   └── name = "101"
                │   └── distance = 200m
         */
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                        key,
                        new Circle(
                                new Point(x,y),
                                new Distance(5000)
                        ),
                        RedisGeoCommands.GeoRadiusCommandArgs
                                .newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(end)
                );

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        // 当前页开始位置已超出查询结果，说明没有更多店铺。
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        /*
         * Redis返回：
         *  shop_id + distance
         * MySQL 负责查询完整店铺数据
         */
        List<Long> shopIds = new ArrayList<Long>();
        Map<String, Distance> distanceMap = new HashMap<String, Distance>();

        content.stream()
                .skip(from)
                .forEach(result ->{
                        String shopId = result.getContent().getName();
                                shopIds.add(Long.valueOf(shopId));
                                distanceMap.put(shopId, result.getDistance());
                    });

        /*
         * IN 查询本身不保证顺序。
         * ORDER BY FIELD 让 MySQL 结果保持 Redis 按距离排序的顺序。
         */
        String idStr = StrUtil.join(",",shopIds);
        List<Shop> shops= query()
                .in("id", shopIds)
                .last("ORDER BY FIELD(id,"+idStr+")")
                .list();

        // 将 Redis 返回的距离填充到非数据库字段 distance。
        for (Shop shop : shops){
            Distance distance = distanceMap.get(shop.getId().toString());
            shop.setDistance(distance.getValue());
        }

        return Result.ok(shops);

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
