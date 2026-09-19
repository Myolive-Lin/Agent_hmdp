# 第 8 课：附近的商户

> 迭代 7｜参考 `dianping`：Redis GEOSEARCH 查找 5km 商户，MySQL 回查完整数据。

## 最终流程

```text
按类型把商户坐标预热到 Redis GEO
  → 按经纬度搜索 5km、距离升序
  → 取得 shopId 和 distance
  → MySQL 批量查询完整 Shop
  → 按 Redis 顺序返回
```

## 任务 1：客户端依赖

参考项目为了使用 `GeoOperations.search`，在 Boot 2.3.12 中配套使用：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.data</groupId>
            <artifactId>spring-data-redis</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
    <version>2.6.2</version>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.1.6.RELEASE</version>
</dependency>
```

这两个版本按参考项目成对使用，JDK 8 可运行。不要只替换其中一个依赖。Redis 服务端必须是 6.2+。

## 任务 2：GEO 预热

Key：

```java
public static final String SHOP_GEO_KEY = "shop:geo:";
```

按 typeId 分组查询商户：

```java
stringRedisTemplate.opsForGeo().add(
        SHOP_GEO_KEY + shop.getTypeId(),
        new Point(shop.getX(), shop.getY()),
        shop.getId().toString()
);
```

- key：`shop:geo:{typeId}`。
- member：shopId。
- point：经度 x、纬度 y。

预热测试可以重复执行；相同 member 会更新坐标。完成后用 Redis `GEOPOS` 检查数据。

## 任务 3：附近商户查询

接口：

```text
GET /shop/of/type?typeId=1&current=1&x=120.1&y=30.2
```

IShopService：

```java
Result queryShopByType(
        Integer typeId,
        Integer current,
        Double x,
        Double y
);
```

没有 x/y 时，按 typeId 走普通 MySQL 分页。存在坐标时：

```java
int from = (current - 1) * DEFAULT_PAGE_SIZE;
int end = current * DEFAULT_PAGE_SIZE;

GeoResults<RedisGeoCommands.GeoLocation<String>> results =
        stringRedisTemplate.opsForGeo().search(
                SHOP_GEO_KEY + typeId,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs
                        .newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(end)
        );
```

必须显式 `sortAscending()`，否则不能保证按距离从近到远。查询先取前 end 条，再跳过 from 条实现课程分页。

## 任务 4：MySQL 保序回查

从结果中建立：

```text
ids = Redis 顺序的 shopId
distanceMap = shopId → 距离
```

再批量查询：

```java
String idStr = StrUtil.join(",", ids);
List<Shop> shops = query()
        .in("id", ids)
        .last("ORDER BY FIELD(id," + idStr + ")")
        .list();
```

ID 全部来自 Redis 中解析成功的 Long。最后把 distance 写入每个 Shop/ShopDTO，再返回。

## 当前实现边界

- 这种“先取 end 再 skip”的方式适合课程数据，深分页开销会逐渐增加。
- 商户坐标或类型变化后要重新 GEOADD，并从旧类型 GEO 中移除；当前可以在更新商户时同步处理，失败时重新执行预热。
- Redis GEO 是索引，MySQL 仍保存完整商户数据。

## 完成标准

- [ ] Redis 6.2+ 与指定 Java 客户端组合能启动。
- [ ] 商户按 typeId 正确预热，member 是 shopId。
- [ ] 无坐标时走 MySQL 分页。
- [ ] 有坐标时只返回 5km 内商户并按距离升序。
- [ ] MySQL 回查后顺序不变，distance 正确写入。
- [ ] 空结果和分页末尾返回空列表。

## 知识点

GEOADD、GEOSEARCH、经纬度、距离排序、分页、MySQL 批量查询、FIELD 保序和 Redis 索引。

[上一课](0007-sign-uv.md)｜[下一课](0009-hardening.md)。
