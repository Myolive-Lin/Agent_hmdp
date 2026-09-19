# Agent HMDP

一个基于 Spring Boot 的黑马点评学习项目。代码以手敲和理解实现为目标：先完成 Redis Token 登录，再逐步实现商户缓存、秒杀、达人探店、关注 Feed、签到、UV 与附近商户等模块。

## 当前技术栈

- JDK 8、Spring Boot 2.3.12、MyBatis-Plus 3.4.3
- MySQL 8、Redis 8、RedisBloom
- Hutool、Lombok

## 当前进度

- Redis Token 手机验证码登录
- 登录拦截器与 Token 刷新
- 商户、商户分类基础结构
- 商户缓存：Bloom Filter、空值缓存、随机 TTL、热点逻辑过期与异步重建（进行中）

## 本地运行

1. 安装 JDK 8、MySQL 和 Redis 8。
2. 创建数据库 `dianping_learning`，并导入所需的 `tb_user`、`tb_shop`、`tb_shop_type` 数据。
3. 复制配置模板并填写本机账号：

   ```bash
   cp src/main/resources/application-example.yaml src/main/resources/application.yaml
   ```

4. 在 IDE 中运行 `com.hmdp.DianpingLearningApplication`。

本仓库忽略 `application.yaml`，避免提交数据库密码。

## 参考与致谢

本项目是个人学习过程中的手敲实现，并非从零定义业务需求。商户点评领域模型、课程主题、基础接口设计与部分实现思路参考了以下本地项目：

- `../dianping`：提供黑马点评主线实现的代码对照，尤其是 `CacheClient`、商户分类缓存、逻辑过期和 GEO 查询思路。
- `../dianping_STMP`：提供 SMTP 登录扩展和缓存策略教学对照，包括缓存穿透、互斥锁、逻辑过期等不同方案的比较素材。

这两个参考项目的源码**不包含**在本仓库中。本仓库只提交学习者在 `dianping-learning` 中手敲、调整和维护的代码；在此感谢原项目及课程作者提供的学习材料。

## 学习原则

- 以可运行的完整模块为单位迭代。
- 不为教学保留需要推翻的临时架构。
- 普通商户采用 Cache Aside、空值缓存与随机 TTL。
- 热点商户采用逻辑过期、随机 owner 锁和 Lua 安全解锁。
- RedisBloom 用于前置过滤无效商户 ID，空值缓存仍用于处理 Bloom 假阳性。
