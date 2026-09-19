# 第 9 课：测试与系统收口

> 迭代 8｜验证前 8 课主链路，修正配置、约束和直接错误，形成可以复现的学习项目。

## 收口范围

当前项目以 `dianping` 和 `dianping_STMP` 为学习参考，不在本课扩展微服务、分布式事务、复杂监控平台或完整生产部署。

## 任务 1：依赖和配置

1. JDK、IDEA、Maven Runner 都使用 Java 8。
2. 删除重复依赖，不单独覆盖无关的 Spring 组件版本。
3. Spring Data Redis 2.6.2 与 Lettuce 6.1.6 只因第 8 课 GEOSEARCH 成对使用。
4. MySQL、Redis、RabbitMQ、SMTP 地址按环境配置。
5. 仓库中没有数据库密码、Token、QQ 邮箱账号和 SMTP 授权码。
6. RabbitMQ AMQP 端口使用 5672，15672 是管理页面。

## 任务 2：数据库约束

检查：

```sql
-- 手机号唯一
ALTER TABLE tb_user
ADD UNIQUE KEY uk_user_phone (phone);

-- 一人一券
ALTER TABLE tb_voucher_order
ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);

-- 关注关系唯一
ALTER TABLE tb_follow
ADD UNIQUE KEY uk_user_follow (user_id, follow_user_id);
```

执行前先查询并处理已有重复数据，不能直接在有重复记录时强行增加索引。

## 任务 3：模块测试

| 模块 | 必测内容 |
|---|---|
| 登录 | 验证码 TTL、错误验证码、首次注册、Token Hash、续期、退出失效 |
| 缓存 | 命中、空值、逻辑过期、锁释放、更新后删除缓存 |
| 秒杀 | 限流、库存不足、一人一单、重复消息、数据库库存不为负 |
| 博客 | 发布者、热榜、点赞切换、点赞榜顺序、图片上传 |
| 关注/Feed | 关注取关、共同关注、发布推送、同毫秒滚动分页 |
| 签到/UV | 月初、断签、连续签到、HLL 重复访客 |
| GEO | 5km 边界、距离升序、分页和 MySQL 保序 |

每个测试只清理自己创建的数据，不执行 `FLUSHALL`，不删除整个本地数据库。

## 任务 4：秒杀核对

```sql
SELECT user_id, voucher_id, COUNT(*) AS count
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;

SELECT voucher_id, stock
FROM tb_seckill_voucher
WHERE stock < 0;
```

两个查询都应返回空结果。测试 RabbitMQ 消息重复消费，数据库仍然只能有一条对应订单。

## 任务 5：接口回归顺序

1. `/ping`。
2. `/user/code`、`/user/login`、`/user/me`、`/user/logout`。
3. 商户分类、详情、更新和缓存。
4. 优惠券发布、秒杀、异步订单。
5. 博客发布、点赞、热榜。
6. 关注、共同关注、Feed。
7. 签到、连续签到、UV。
8. 附近商户。

用 curl/Postman/Apifox 保存请求示例。登录后统一通过：

```text
Authorization: <token>
```

## 任务 6：README

README 至少记录：

- JDK、MySQL、Redis、RabbitMQ 版本。
- 数据库初始化方式。
- 本地配置和需要的环境变量名。
- 启动、测试和打包命令。
- 9 个模块的主要接口。
- Redis key 说明。
- 当前实现边界和后续可选优化。

后续可选优化单独列出，不混入当前手敲主线：验证码滑动窗口限流、异步邮件、缓存删除补偿、MQ Confirm/Return、秒杀库存补偿、Feed 异步扇出和系统监控。

## 完成标准

- [ ] 从空数据库和空 Redis 可以完成初始化并启动。
- [ ] `mvn test`、`mvn package` 通过。
- [ ] 所有登录代码不使用 HttpSession，Redis Token 可以续期和退出失效。
- [ ] 关键唯一索引存在，秒杀不超卖、不重复下单。
- [ ] 九个模块可以按 README 独立演示。
- [ ] 能解释每个 Redis 数据结构解决什么问题。
- [ ] 已知边界写清楚，没有把可选优化描述成已实现功能。

## 最终复盘

不看代码，画出并讲清：

1. Redis Token 登录链路。
2. 空值缓存和逻辑过期链路。
3. Lua + RabbitMQ 秒杀链路。
4. 关注 + ZSet Feed 链路。
5. Bitmap、HyperLogLog 和 GEO 的数据模型。

[上一课](0008-nearby-shops.md)｜[回到第 1 课](0001-foundation.md)。
