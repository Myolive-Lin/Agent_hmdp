package com.hmdp.utils;

public final class RedisConstants {

    private RedisConstants(){
    }

    /*
     * ---------------- 登录模块 ----------------
     */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final long LOGIN_CODE_TTL = 2L;

    //每个Token对应一份用户登录信息
    public static final String LOGIN_USER_KEY = "login:token:";

    // 登录有效期
    public static final long LOGIN_USER_TTL = 30L;

    /*
     * ---------------- 商户缓存模块 ----------------
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    //普通商品缓存的ttl
    public static final long CACHE_SHOP_TTL = 30L;

    //在TTL的基础上随机加上 0~5分钟，避免key同时大量失效
    public static final long CACHE_SHOP_TTL_RANDOM = 5L;

    //热点商品逻辑过期时间
    public static final long CACHE_SHOP_LOGICAL_TTL = 30L;

    //不存在商品空值缓存TTL
    public static final long CACHE_NULL_TTL = 2L;

    //热点商品缓存重建锁 lock:shop:1
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    // 必须大于一次正常数据库查询的最长时间
    public static final long LOCK_SHOP_TTL = 10L;

    //商品分类 Redis List
    public static final String SHOP_TYPE_KEY = "shop:type:list";

    // RedisBloom的key，不设置TTL
    public static final String SHOP_BLOOM_KEY = "bf:shop:id";

    // 库存
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    // 用户Order
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    public static final String BLOG_LIKED_KEY = "blog:liked:";

    //当前用户关注的用户ID集合
    public static final String FOLLOW_KEY = "follows:";

    //每个用户的Feed收件箱
    public static final String FEED_KEY = "feed:";

    /**
     * 用户月度签到 Bitmap。
     * 示例：sign:5:202609
     */
    public static final String USER_SIGN_KEY = "sign:";


    /**
     * 每日页面 UV 的 HyperLogLog。
     * 示例：uv:home:20260924
     */
    public static final String UV_KEY = "uv:";

}
