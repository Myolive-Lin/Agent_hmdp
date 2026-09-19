# 第 1 课：工程底座

> 迭代 0｜以本地 `dianping` 和 `dianping_STMP` 的工程结构为主，在 `dianping-learning` 中自己手敲。

## 本课程的实现原则

- 课程固定为 9 个敏捷迭代，每课完成可运行、可验证的功能。
- 当前学习参考本地 `../dianping`；邮箱扩展参考 `../dianping_STMP`。
- 先掌握参考项目已有实现，不提前增加 Outbox、复杂状态机、版本投影等扩展设计。
- 登录统一采用 Redis Token，不使用 `HttpSession` 保存或传递登录状态。
- 明确的代码错误会直接修复，例如 TTL 单位错误、条件写反、验证码格式错误和退出不删除 Token。

## 技术基线

| 项目 | 版本 |
|---|---|
| Java | 8 |
| Spring Boot | 2.3.12.RELEASE |
| MyBatis-Plus | 3.4.3 |
| MySQL | 8.x，驱动使用 8.0.28 |
| Redis | 6.2+；本机 Redis 7 可用 |
| Hutool | 5.7.17 |
| Lombok | 由 Spring Boot 管理 |
| RabbitMQ | 秒杀异步下单时加入 |

## 本课依赖

在当前 `pom.xml` 的基础上确认存在：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.4.3</version>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.28</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.7.17</version>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

RabbitMQ、Redisson、邮件等依赖在对应课程加入，避免本课同时配置尚未使用的组件。IDEA 开启 Annotation Processing。

## 工程结构

```text
com.hmdp
├── controller
├── service
│   └── impl
├── mapper
├── entity
├── dto
├── config
├── interceptor
└── utils
```

Service 延续参考项目命名：`IUserService`、`IShopService`；实现类为 `UserServiceImpl`、`ShopServiceImpl`。实体和 DTO 使用 Lombok，不再手写 getter/setter：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_user")
public class User implements Serializable {
    // 字段对照 tb_user
}
```

## 统一返回

`Result` 从第一课保留参考项目的四个字段：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result ok() {
        return new Result(true, null, null, null);
    }
    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }
    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }
    public static Result fail(String message) {
        return new Result(false, message, null, null);
    }
}
```

## 实施步骤

1. 完成 Maven 依赖和包结构。
2. 创建启动类并使用 `@MapperScan("com.hmdp.mapper")`。
3. 创建 `GET /ping`，MockMvc 和 curl 都返回 `pong`。
4. 导入/创建课程数据库表，确认 `tb_user.phone` 有唯一索引。
5. 配置 MySQL、Redis；密码通过本机配置或环境变量提供，不提交真实密码。
6. 创建 User、UserMapper，并用事务回滚测试验证 MyBatis-Plus。
7. 为 Redis 写一个带 TTL 的读写测试，测试结束删除自己的 key。

## 完成标准

- [ ] JDK、IDEA Project SDK、Maven Runner 都是 Java 8。
- [ ] `mvn test` 和启动均成功。
- [ ] MySQL、Redis 能正常读写。
- [ ] Result、实体、DTO 使用最终 Lombok 结构。
- [ ] 仓库中没有数据库密码、QQ SMTP 授权码和 Token。

下一课：[Redis Token 登录](0002-redis-token-login.md)。
