package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    /*
     * 只有锁的持有者才能删除锁。
     *
     * KEYS[1]：锁 Key
     * ARGV[1]：当前线程的随机 owner
     */
    //有一个Lua脚本对象
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<Long>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1] == ARGV[1])"
                + "then return redis.call('del', KEYS[1])"
                +"else return 0 end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "cacheRebuildExecutor")
    private Executor cacheRebuildExecutor;

    /*
     * 普通缓存写入，Redis key 有物理TTL；
     */
    public void set(String key, Object value, long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(value),
                time,
                unit
        );
    }

    /*
     * 热点缓存写入： key不设置TTL
     * 数据本身包含 expireTime
     * 即使逻辑过期，也返回旧值给请求使用
     */
    public void setWithLogicalExpire(
            String key,
            Object value,
            long time,
            TimeUnit unit
    ){
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);

        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(redisData)
        );
    }

    /*
     * 普通商户查询：
     *
     * Redis JSON 命中  -> 返回数据
     * Redis "" 命中    -> 返回 null，代表数据库不存在
     * Redis 未命中     -> 查询 MySQL 后写入缓存
     *
     * randomTtlBound 只在“回写 Redis”时计算，
     * 不会在每一次读取请求时重新随机。
     */

    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID,R> dbFallback,
            long baseTtl,
            long randomTtlRound,
            TimeUnit unit
    ){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }

        //命中空字符串，之前已经确认过Mysql没有该数据
        if (json != null){
            return null;
        }

        // Redis 未命中，回源 MySQL
        R result = dbFallback.apply(id);

        // MySQL 也不存在：短时间缓存空字符串，防止穿透
        if (result == null){
            stringRedisTemplate.opsForValue().set(
                    key,
                    "",
                    RedisConstants.CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
        }

        //存在，存储加上随机，防止缓存雪崩
        long ttl = baseTtl + randomOffset(randomTtlRound);
        set(key, result, ttl, TimeUnit.MINUTES);
        return result;
    }

    /*
     * 热点商品查询
     * 前提：热点商品经过ShopCacheInitializer 预热。
     *
     * 未逻辑过期：直接返回
     * 已过期逻辑：返回旧数据，同时仅允许一个线程异步刷新
     */
    public  <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long logicalTtl,
            TimeUnit unit
    ){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        /*
         * 热点缓存丢失时
         *
         *正常情况由启动预热、更新后的下一次加载保证。
         */
        if (StrUtil.isBlank(json)){
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = JSONUtil.parseObj(redisData.getData()); //解析成JSON格式，然后再转换成对应的数据对象
        R result = JSONUtil.toBean(data, type);

        // 尚未逻辑过期， 直接返回缓存
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return result;
        }

        //逻辑过期，获得锁，使用线程池异步重建结果和更新缓存，期间先返回旧数据
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String owner = tryLock(lockKey);
        try{
            if (owner != null){
                cacheRebuildExecutor.execute(
                        () -> {
                            try {
                                R freshResult = dbFallback.apply(id);

                                if (freshResult == null) {
                                    // 数据已被删除，删除旧逻辑缓存
                                    stringRedisTemplate.delete(key);
                                    return;
                                }

                                //把结果重新加入缓存中
                                setWithLogicalExpire(key, freshResult, logicalTtl, unit);
                            } catch (Exception e) {
                                log.error("热点缓存重建失败，key={}", key, e);
                            } finally {
                                // Lua 比较owner后删除，避免误删其他线程的锁
                                unlock(key, owner);
                            }
                        }
                );
            }
        }catch (TaskRejectedException e){
            // 线程池满时，当前线程未执行，当前线程必须释放自己的锁。

            unlock(key, owner);
            log.warn("缓存重建线程池繁忙，key={}", key);
        }

        // 逻辑过期后，仍然返回旧数据
        return result;
    }




    /*
     * SET key owner NX EX 10
     *
     * owner 是随机UUID，而不是固定字符串 "1"。
     */
    private String tryLock(String key){
        String owner = UUID.randomUUID().toString();

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                owner,
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        );

        return Boolean.TRUE.equals(success) ? owner : null;
    }


    private void unlock(String key, String owner){
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                owner
        );
    }





    private long randomOffset(long randomTtlBound){
        if (randomTtlBound <= 0L){
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(randomTtlBound + 1L);
    }
}
