# 第 7 课：用户签到与 UV 统计

> 迭代 6｜参考两个本地项目：Redis Bitmap 记录签到，HyperLogLog 统计独立访客。

## 一、用户签到

### Redis 结构

```text
sign:{userId}:{yyyyMM}
```

一个用户每个月使用一个 String 的位图。每月第 d 天对应 offset=`d-1`。

### 任务 1：签到

接口：

```text
POST /user/sign
```

核心逻辑：

```java
Long userId = UserHolder.getUser().getId();
LocalDateTime now = LocalDateTime.now();
String suffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
String key = USER_SIGN_KEY + userId + suffix;
int dayOfMonth = now.getDayOfMonth();
stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
return Result.ok();
```

重复签到只是再次把同一位设为 1，不会创建重复记录。

### 任务 2：连续签到

接口：

```text
GET /user/sign/count
```

使用 BITFIELD 读取本月 1 日到今天：

```java
List<Long> result = stringRedisTemplate.opsForValue().bitField(
        key,
        BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0)
);
```

从最低位开始判断：

```java
long num = result.get(0);
int count = 0;
while ((num & 1L) == 1L) {
    count++;
    num >>>= 1;
}
return Result.ok(count);
```

今天对应最低位；今天没签到直接得到 0。当前统计的是“本月至今天连续签到”，不跨月。

## 二、UV 统计

### HyperLogLog 练习

先按参考课程写集成测试：

1. 向 `hll:test` 分批 PFADD 大量不同 visitorId。
2. 重复添加一部分 visitorId。
3. PFCOUNT 查看估算人数。
4. 测试结束删除 `hll:test`。

HyperLogLog 是近似统计，不要求结果与实际数量完全相同，也不能取回访客列表。

### 正式业务 Key

```text
uv:{pageId}:{yyyyMMdd}
```

如果项目需要实际页面 UV，增加两个简单接口：

- `POST /uv/{pageId}`：PFADD 当前 visitorId。
- `GET /uv/{pageId}?date=yyyyMMdd`：PFCOUNT，查询接口需要管理权限。

visitorId 使用浏览器首次访问时生成的随机 Cookie，后续请求保持不变。pageId 使用固定白名单，不能允许任意字符串无限创建 Redis key。每日 key 可以设置 30 天 TTL。

当前只实现日 UV，不增加复杂的设备识别、反作弊或实时分析系统。

## 完成标准

- [ ] 第 d 天写入 offset=d-1。
- [ ] 重复签到不影响结果。
- [ ] 今天未签到、连续签到和中间断签计算正确。
- [ ] HyperLogLog 重复添加同一 visitorId 不按访问次数累加。
- [ ] UV 接口使用固定 pageId 和日期 key，查询有权限限制。
- [ ] 能解释 Bitmap 是精确位记录，HyperLogLog 是近似基数统计。

## 知识点

Bitmap、SETBIT、BITFIELD、位运算、HyperLogLog、PFADD、PFCOUNT、基数估算和 Redis 空间优化。

[上一课](0006-follow-feed.md)｜[下一课](0008-nearby-shops.md)。
