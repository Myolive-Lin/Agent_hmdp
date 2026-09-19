# 第 4 课：优惠券秒杀与异步下单

> 迭代 3｜参考两个本地项目：Guava 令牌桶 + Redis ID + Lua + RabbitMQ。

## 最终链路

```text
请求限流
  → Redis Lua 判断库存和一人一单
  → Redis 预扣库存并记录用户
  → 生成订单并发送 RabbitMQ
  → 消费者事务扣减 MySQL 库存并保存订单
```

本课程使用 RabbitMQ，不同时实现 Redis Stream、JVM 阻塞队列等其他异步方案。

## 任务 1：依赖和表约束

加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>19.0</version>
</dependency>
```

创建 Voucher、SeckillVoucher、VoucherOrder、Mapper、IService、ServiceImpl。数据库必须增加：

```sql
ALTER TABLE tb_voucher_order
ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
```

唯一索引是并发下“一人一单”的最后保护。

## 任务 2：Redis ID

创建 `RedisIdWorker`，参考项目使用：

```text
64 位 ID = 32 位时间戳差值 + 32 位 Redis 当日自增序列
```

订单不使用数据库自增 ID，避免高并发下依赖单表自增，也便于消息发送前先确定 orderId。

## 任务 3：秒杀 Lua

Redis：

```java
public static final String SECKILL_STOCK_KEY = "seckill:stock:";
```

- `seckill:stock:{voucherId}`：String 库存。
- `seckill:order:{voucherId}`：Set，保存成功取得资格的 userId。

活动发布时把库存写入 Redis。`seckill.lua` 只完成：

1. 检查库存 key 是否存在且库存大于 0。
2. SISMEMBER 判断用户是否已下单。
3. DECR 库存。
4. SADD userId。
5. 返回 0；库存不足返回 1；重复下单返回 2。

脚本不再执行参考 `dianping` 中的 `XADD stream.orders`，因为当前异步方案固定为 RabbitMQ。

## 任务 4：令牌桶和入口

在 VoucherOrderServiceImpl 中创建单例 RateLimiter：

```java
private final RateLimiter rateLimiter = RateLimiter.create(10.0);
```

入口使用 `tryAcquire()`；拿不到令牌直接返回“系统繁忙”，不要长时间阻塞 HTTP 线程。

下单顺序：

1. 获取当前 userId。
2. 先生成 orderId。
3. 执行 Lua，参数为 voucherId、userId。
4. Lua 返回 0 后创建 VoucherOrder。
5. 使用 RabbitTemplate 发送订单消息。
6. 返回 orderId，表示请求已进入异步处理。

令牌桶是单 JVM 限流；部署多个实例时总速率会相加，这是当前实现边界。

## 任务 5：RabbitMQ

创建 durable DirectExchange、durable Queue、Binding 和死信队列。消息使用 JSON，不使用 Java 原生序列化。

消费者收到 VoucherOrder 后，在一个 `@Transactional` 方法中：

1. 根据 orderId 查询，已经存在则当作重复消息结束。
2. 查询 `(user_id, voucher_id)`，已有订单则结束。
3. 条件扣减 MySQL 库存：

```java
boolean success = seckillVoucherService.update()
        .setSql("stock = stock - 1")
        .eq("voucher_id", voucherId)
        .gt("stock", 0)
        .update();
```

4. 扣减失败必须抛异常，使事务回滚；不能只记录日志后继续保存订单。
5. 保存 VoucherOrder；保存失败同样抛异常。
6. 事务成功后确认消息；失败按配置重试，最终进入死信队列。

消息可能重复投递，因此消费者必须依靠订单主键和用户优惠券唯一索引保证幂等。

## 当前实现边界

- Lua 只能保证 Redis 内判断和预扣原子，不包含 RabbitMQ 和 MySQL。
- Redis 预扣成功后 RabbitMQ 发送失败，会产生 Redis/MySQL 暂时不一致。当前先按参考项目掌握主链路，后续系统优化再增加可靠事件和补偿。
- 接口返回 orderId 代表已受理，不代表订单已经落库。
- RabbitMQ 的 Confirm、Return、重试和补偿是后续可靠性强化，不在当前手敲主线中展开复杂状态机。

## 完成标准

- [ ] 令牌桶能拒绝超过设置速率的请求。
- [ ] Lua 原子判断库存、一人一单并预扣库存。
- [ ] 成功请求进入 RabbitMQ，不使用 Redis Stream。
- [ ] 消费者事务中条件扣库存并保存订单。
- [ ] 重复消息不重复扣库存或创建订单。
- [ ] 数据库库存不为负，同用户同券最多一条订单。

## 知识点

令牌桶、Redis 全局 ID、Lua 原子性、Set、一人一单、RabbitMQ 削峰、数据库事务、乐观条件更新、消息幂等和死信队列。

[上一课](0003-shop-cache.md)｜[下一课](0005-blog.md)。
