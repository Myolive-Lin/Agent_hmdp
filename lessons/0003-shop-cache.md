# 第 3 课：商户缓存与 Redis Bloom

> 迭代 2｜参考 `dianping` 的 CacheClient；在此基础上加入 RedisBloom。Redis 4.0～7.x 通过模块提供，Redis 8 起成为 Redis Open Source 的内置组成部分。

## 本课目标

完成商户分类、商户详情、商户更新，并让主查询真正经过 Bloom Filter 和 CacheClient。

| 问题 | 本项目方案 |
|---|---|
| 缓存穿透 | Redis Bloom 拦截无效 ID，空值缓存兜住误判 |
| 缓存击穿 | 热点数据逻辑过期 + Redis 锁 + 异步重建 |
| 缓存雪崩 | 不同数据设置不同/随机 TTL，避免同时失效 |
| 数据更新 | 先更新 MySQL，再删除 Redis 缓存 |

查询链路固定为：

```text
请求 ID → Bloom 判断 → Redis 缓存 → MySQL → 回写缓存
```

Bloom 返回不存在时可以直接结束；返回存在只代表“可能存在”，仍要查询缓存和 MySQL。Bloom 存在误判，因此不能删除空值缓存。

## 先理解：三类缓存问题在本课都要处理

它们不是三个可任选的“优化功能”，而是三种不同的访问异常。一个商户详情接口同时需要防穿透、抗击穿，并避免大范围雪崩；只是在不同条件下走不同分支。

| 问题 | 请求发生了什么 | 不处理的后果 | 本项目最终方案 | 关键边界 |
|---|---|---|---|---|
| 缓存穿透 | 请求的商户 ID 在 MySQL 中本来就不存在，例如恶意连续请求 `99999999` | 每次 Redis 未命中后都访问 MySQL | Bloom Filter 前置拦截 + 空值缓存 | Bloom `false` 才能直接拒绝；`true` 仍可能是假阳性 |
| 缓存击穿 | 一个真实、热门商户的缓存刚好失效，瞬间有大量请求同时到达 | 大量线程同时回源 MySQL 查询同一条热点数据 | 热点商户逻辑过期 + 互斥重建锁 + 异步重建 | 返回旧值，只有拿到锁的线程重建 |
| 缓存雪崩 | 大批缓存 Key 在相近时间一起失效，或 Redis 整体不可用 | 大量不同请求同时绕过缓存，MySQL 被压垮 | 普通 Key 的 TTL 加随机值；Redis 高可用属于部署层后续工作 | 随机 TTL 只能错开失效时间，不能替代 Redis 高可用 |

### 1. 缓存穿透：为什么还要保留空值缓存

假设有人不断请求不存在的 `shopId=99999999`：

```text
没有保护：请求 → Redis miss → MySQL 不存在 → Redis miss → MySQL 不存在 → ...
最终方案：请求 → Bloom false → 直接返回“商户不存在”
```

但 Bloom Filter 允许假阳性：它可能对一个不存在的 ID 返回 `true`，却绝不会对已经写入的 ID 返回 `false`。因此这条路径仍需空值缓存兜底：

```text
Bloom true → Redis miss → MySQL 不存在 → 写入 ""（2 分钟）
下次请求 → Bloom true → Redis 命中 "" → 直接返回不存在
```

所以，本项目中 Bloom 负责拦截绝大多数无效 ID；空值缓存负责吸收 Bloom 假阳性、Bloom 初始化遗漏以及短期数据库不存在查询。空值必须是 `""`，不能向 `StringRedisTemplate` 写入 Java 的 `null`。

### 2. 缓存击穿：为什么热点商户不能只靠 TTL

普通商户失效时，只有少量请求回源 MySQL，使用 TTL 即可。热点商户（例如首页反复访问的店铺）失效时，成千上万请求可能在同一时刻发现缓存不存在；这叫击穿，不是穿透，因为数据在 MySQL 中真实存在。

```text
普通 TTL：缓存到期 → N 个请求都 miss → N 次查询 MySQL
逻辑过期：缓存逻辑到期 → N 个请求仍读到旧数据
                         └─ 仅 1 个请求拿到 Redis 锁，异步查询 MySQL 并重建
```

逻辑过期不是 Redis 的物理 TTL：Redis 中保存 `RedisData { data, expireTime }`，Key 本身不在到点时删除。请求发现 `expireTime` 已过，仍先返回旧数据；获得锁的线程在后台刷新。这是“可接受短暂旧数据”换取“保护数据库”的设计，适合商户详情这类读多、允许短暂不一致的热点数据。

### 3. 缓存雪崩：为什么普通 Key 的 TTL 要加随机值

若一批商户数据都在 `12:00` 写入、统一设置 30 分钟 TTL，它们会在 `12:30` 集体过期。不同商户的请求会一起回源 MySQL，这就是雪崩；与“一个热点 Key 被大量请求打穿”的击穿不同。

本课普通商户的缓存过期时间采用“基础 TTL + 小范围随机值”，例如：

```java
long ttlMinutes = 30L + ThreadLocalRandom.current().nextLong(0L, 6L);
```

这里的随机值必须在**写入缓存时**计算，而不是每次读取时计算。空值缓存仍使用固定且更短的 2 分钟 TTL，避免不存在数据长期被缓存。Redis 整体宕机导致的雪崩属于部署问题，后续通过 Redis 主从/哨兵或集群、限流和降级处理；本课不伪造一个单机 Java 方案来解决它。

### 最终查询决策

```text
GET /shop/{id}
  ├─ Bloom false                         → 返回不存在（防穿透）
  └─ Bloom true
       ├─ id 是热点商户                  → 逻辑过期缓存
       │    ├─ 未逻辑过期                → 返回缓存
       │    └─ 已逻辑过期                → 返回旧值；抢锁成功者异步重建（防击穿）
       └─ id 是普通商户                  → Cache Aside + 空值缓存 + 随机 TTL
            ├─ JSON 命中                 → 返回缓存
            ├─ "" 命中                   → 返回不存在（防穿透）
            └─ 未命中                    → 查询 MySQL 后回写
```

这不是先做“穿透版”、以后再推翻为“击穿版”。第一次实现 `queryById()` 就按这张决策图分流；任务的顺序只是在组织你手敲的文件依赖关系。

## 环境前置：确认 RedisBloom 可用

需要区分“Redis 支持加载模块”和“Redis 发行版自带 Bloom”：

| Redis 版本/发行版 | Bloom Filter 情况 |
|---|---|
| Redis 4.0～7.x 普通版 | 从 4.0 起支持 Modules API，但必须另外编译/加载 RedisBloom，普通安装没有 `BF.*` |
| Redis Stack 6/7 | 已捆绑 RedisBloom，可以直接使用 `BF.*` |
| Redis Open Source 8+ | RedisBloom 已成为发行版的内置组成部分，不再单独安装 |

因此，“Redis 4.0 开始可以通过模块启用 RedisBloom”是正确的；但这不等于“安装普通 Redis 4.0 后就自带 Bloom”。RedisBloom 使用 `BF.RESERVE`、`BF.ADD`、`BF.EXISTS` 等命令。

当前本机已经完成切换：

```text
dianping-redis8   Redis 8.10.1   运行中   端口 6379
docker-redis-1    Redis 7.4.11   已停止   原数据仍保留
```

实际执行的切换命令如下：

```bash
docker pull redis:8
docker stop docker-redis-1
docker run -d \
  --name dianping-redis8 \
  --restart unless-stopped \
  -p 6379:6379 \
  -v dianping-redis8-data:/data \
  redis:8 \
  redis-server \
  --appendonly yes \
  --appendfsync everysec
```

新容器使用独立数据卷 `dianping-redis8-data`，不会修改旧容器的数据卷 `docker_redis-data`。项目仍连接默认端口 `6379`，`application.yaml` 不需要改为 6380。

检查 Redis 版本和 Bloom 命令：

```bash
docker exec dianping-redis8 redis-cli INFO server
docker exec dianping-redis8 redis-cli COMMAND INFO BF.EXISTS
```

当前已经验证 `BF.EXISTS` 可用。最终课程直接使用 Redis 8 内置方案，不自行编译 C 模块，也不在 Java 中实现位数组和哈希算法。

需要恢复旧 Redis 时，先释放 6379，再启动旧容器：

```bash
docker stop dianping-redis8
docker start docker-redis-1
```

两个容器不能同时运行，因为都会占用宿主机的 `6379`。

如果必须保留 Redis 4～7，也可以加载编译后的 RedisBloom 动态库或直接使用包含该模块的 Redis Stack；业务代码仍然调用相同的 `BF.*` 命令。

官方资料：[Redis Modules API](https://redis.io/docs/latest/develop/reference/modules/)｜[RedisBloom 官方仓库](https://github.com/RedisBloom/RedisBloom)｜[Redis Bloom Filter](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/)｜[BF.RESERVE](https://redis.io/docs/latest/commands/bf.reserve/)｜[Redis 8 模块生命周期](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/modules-lifecycle/)

## Redis 数据

```java
public static final String CACHE_SHOP_KEY = "cache:shop:";
public static final Long CACHE_SHOP_TTL = 30L;
public static final Long CACHE_NULL_TTL = 2L;
public static final String LOCK_SHOP_KEY = "lock:shop:";
public static final Long LOCK_SHOP_TTL = 10L;
public static final String SHOP_TYPE_KEY = "shop_type:";
public static final String SHOP_BLOOM_KEY = "bf:shop:id";
```

## 任务 1：基础接口

创建 Shop、ShopType、Mapper、IService、ServiceImpl 和 Controller：

- `GET /shop/{id}`：查询商户详情。
- `PUT /shop`：更新商户并删除缓存。
- `GET /shop-type/list`：分类列表，先查 Redis，未命中查 MySQL。

实体使用 Lombok，Service 延续 `IShopService` 命名。

## 任务 2：初始化 Redis Bloom

创建过滤器：

```redis
BF.RESERVE bf:shop:id 0.001 100000
```

- `0.001`：允许约 0.1% 假阳性。
- `100000`：预计最多保存 10 万个商户 ID。
- Bloom Key 不设置 TTL，否则过滤器过期后会失去保护。

将 MySQL 中已有的全部商户 ID 批量写入 Bloom；以后创建新商户时，在 MySQL 事务提交后执行 `BF.ADD`。普通 Bloom Filter 不支持安全删除单个元素，商户删除后留下的旧 ID 只会使 Bloom 继续返回“可能存在”，最终仍由 MySQL 和空值缓存判断不存在；需要彻底清理时使用新 Key 全量重建后再切换。

Spring Data Redis 2.3 没有专用 Bloom API，使用底层 Redis 命令，不需要增加 Java 依赖：

```java
@Component
public class ShopBloomFilter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean mightContain(Long shopId) {
        Object result = stringRedisTemplate.execute(
                (RedisCallback<Object>) connection -> connection.execute(
                        "BF.EXISTS",
                        bytes(RedisConstants.SHOP_BLOOM_KEY),
                        bytes(shopId)
                )
        );
        return result instanceof Number
                && ((Number) result).longValue() == 1L;
    }

    public void add(Long shopId) {
        stringRedisTemplate.execute(
                (RedisCallback<Object>) connection -> connection.execute(
                        "BF.ADD",
                        bytes(RedisConstants.SHOP_BLOOM_KEY),
                        bytes(shopId)
                )
        );
    }

    private byte[] bytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }
}
```

需要导入：

```java
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
```

初始化必须满足两个条件：

1. 先执行 `BF.RESERVE`，再写入全部已有商户 ID。
2. 过滤器构建完成前不能启用“Bloom 不存在就直接返回”的查询逻辑，否则漏写的真实商户会被错误拦截。

## 任务 3：CacheClient 普通缓存

实现：

```java
set(String key, Object value, Long time, TimeUnit unit)
queryWithPassThrough(
        String keyPrefix,
        ID id,
        Class<R> type,
        Function<ID, R> dbFallback,
        Long time,
        TimeUnit unit
)
```

`queryWithPassThrough` 继续保留空值缓存。商户查询入口先执行 Bloom 判断：

```java
if (!shopBloomFilter.mightContain(id)) {
    return Result.fail("商户不存在");
}

Shop shop = cacheClient.queryWithPassThrough(
        CACHE_SHOP_KEY,
        id,
        Shop.class,
        this::getById,
        CACHE_SHOP_TTL,
        TimeUnit.MINUTES
);
```

Bloom 返回 `true` 后，`queryWithPassThrough` 顺序：

1. GET Redis。
2. 非空 JSON：反序列化并返回。
3. 值为 `""`：说明数据库不存在，返回 null。
4. Redis 未命中：执行 dbFallback。
5. DB 不存在：缓存 `""`，TTL 2 分钟。
6. DB 存在：缓存 JSON，TTL 30 分钟后返回。

为什么 Bloom 和空值缓存都保留：

- Bloom 返回 `false`：一定不存在，直接拦截，不访问 MySQL。
- Bloom 返回 `true`：只是可能存在；假阳性仍会到达 MySQL。
- MySQL 查询不存在：写入短 TTL 空值，避免同一个假阳性 ID 反复访问数据库。

## 任务 4：逻辑过期

创建 RedisData：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
```

实现：

```java
setWithLogicalExpire(...)
queryWithLogicalExpire(...)
```

查询顺序：

1. 热点数据必须先预热为 RedisData JSON。
2. Redis 未命中返回 null；本方案依赖热点预热。
3. 未到逻辑过期时间，直接返回商户。
4. 已逻辑过期，尝试获得 `lock:shop:{id}`。
5. 获锁后提交线程池，从 MySQL 查询并重写逻辑过期时间。
6. 当前请求立即返回旧数据，不等待重建。
7. finally 释放锁。

锁值使用随机 owner，释放锁使用 Lua“比较 owner 后删除”，避免误删其他线程后来获得的锁。这是对参考代码直接删除锁的必要修正。

## 任务 5：更新商户

```java
@Transactional
public Result updateShop(Shop shop) {
    if (shop.getId() == null) {
        return Result.fail("商户id不能为空");
    }
    updateById(shop);
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
    return Result.ok();
}
```

这是 Cache Aside 的最终一致方案：删除缓存失败可能短暂读到旧值，TTL 最终会使其失效；当前课程不加入消息补偿系统。

## 完成标准

- [ ] 商户查询真正调用 CacheClient。
- [ ] Redis 支持 `BF.RESERVE/BF.ADD/BF.EXISTS`，过滤器已写入全部现有商户 ID。
- [ ] Bloom 判断不存在时不查询缓存和 MySQL。
- [ ] Bloom 判断可能存在时继续经过 CacheClient，不能把 `true` 当成一定存在。
- [ ] 缓存命中时不查询 MySQL。
- [ ] 不存在商户写入空字符串，TTL 约 2 分钟。
- [ ] 热点商户已预热，逻辑过期时返回旧值并异步重建。
- [ ] 多线程重建只有获得锁的线程执行，锁不会被其他线程误删。
- [ ] 更新商户后删除缓存。
- [ ] 新增商户写入 MySQL 成功后同步执行 `BF.ADD`。

## 知识点

Cache Aside、JSON 序列化、Redis Bloom、假阳性、容量与误判率、空值缓存、缓存穿透、缓存击穿、缓存雪崩、逻辑过期、Redis 锁、异步重建、最终一致性。

[上一课](0002-redis-token-login.md)｜[下一课](0004-seckill-orders.md)。
