# HMDP Learning Project

基于 Spring Boot 的黑马点评学习项目。仓库记录从工程底座、Redis Token 登录到商户缓存的手敲实现过程；它是学习中的进行中项目，不是生产系统或黑马官方代码。

## 当前范围

- Redis Token 手机验证码登录、Token 刷新与登录拦截器
- 商户与商户分类基础接口
- 商户缓存：RedisBloom、空值缓存、随机 TTL、热点数据逻辑过期与异步重建

后续计划包括优惠券秒杀、RabbitMQ 异步下单、达人探店、关注 Feed、签到、UV 和附近商户。

## 学习版本

仓库中的 `lessons/` 按敏捷迭代组织。每个版本表示一个可学习、可验证的课程目标；它不表示后续课程中的功能已经实现。

| 版本 | 课程 | 迭代目标 |
|---|---|---|
| `v0.1.0` | 工程底座 | 可启动工程、基础配置与测试连接 |
| `v0.2.0` | Redis Token 登录 | 验证码、Token、拦截器与续期 |
| `v0.3.0` | 商户缓存 | RedisBloom、Cache Aside 与热点缓存 |
| `v0.4.0` | 秒杀订单 | Lua 资格校验、限流与 RabbitMQ |
| `v0.5.0` | 达人探店 | 博客、点赞与热榜 |
| `v0.6.0` | 关注 Feed | 关注关系、共同关注与推送 |
| `v0.7.0` | 签到与 UV | Bitmap 与 HyperLogLog |
| `v0.8.0` | 附近商户 | GEOSEARCH 与距离排序 |
| `v1.0.0` | 测试与收口 | 测试、约束、配置与可靠性检查 |

对应课件见 [`lessons/`](lessons/)。

## 技术栈

- JDK 8、Spring Boot 2.3.12、MyBatis-Plus 3.4.3
- MySQL、Redis 8 / RedisBloom
- Lombok、Hutool

## 本地运行

1. 准备 JDK 8、MySQL 与 Redis 8。
2. 创建 `dianping_learning` 数据库并准备 `tb_user`、`tb_shop`、`tb_shop_type` 表及测试数据。
3. 创建本地配置：

   ```bash
   cp src/main/resources/application-example.yaml src/main/resources/application.yaml
   ```

4. 修改 `application.yaml` 中的数据库账号、密码和热点商户 ID。
5. 在 IDE 中运行 `com.hmdp.DianpingLearningApplication`。

`application.yaml` 已被 Git 忽略，避免提交本机凭据。

## 参考与致谢

本仓库中的代码由学习者在学习过程中手敲、调整和维护；业务题材、模块划分、领域模型及部分实现思路参考了以下开源项目。它们的源码、前端资源和数据库文件均未包含在本仓库中。

- [KNeegcyao/dianping](https://github.com/KNeegcyao/dianping)：作为黑马点评完整实现的主线代码对照，主要参考 Redis Token 登录、商户缓存、分类缓存、逻辑过期、GEO 查询及 Redis 数据结构的应用思路。
- [haopengmai/dianping](https://github.com/haopengmai/dianping)：作为扩展实现对照，主要参考 SMTP 邮箱验证码、RabbitMQ 异步秒杀、令牌桶限流和基于 Redis ZSet 时间窗口的登录限流思路。

感谢上述项目作者及黑马点评课程提供的学习材料。若本仓库后续采用或改编了其他开源代码，会在对应提交或文档中继续标注来源。
