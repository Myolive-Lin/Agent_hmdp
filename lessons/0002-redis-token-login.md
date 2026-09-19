# 第 2 课：Redis Token 登录

> 迭代 1｜主线参考 `dianping`：手机号验证码 + Redis Token；邮件登录完成主线后参考 `dianping_STMP`。

## 本课目标

```text
发送验证码 → Redis String
校验验证码 → 查询/创建用户
生成 Token → Redis Hash
拦截器读取 Token → ThreadLocal
```

本课不使用 `HttpSession`。Controller、IUserService、UserServiceImpl 的方法参数都不出现 Session。

## Redis 数据

| Key | 类型 | 内容 | TTL |
|---|---|---|---|
| `login:code:{phone}` | String | 6 位数字验证码 | 2 分钟 |
| `login:token:{token}` | Hash | UserDTO | 30 分钟，访问时续期 |

`RedisConstants`：

```java
public static final String LOGIN_CODE_KEY = "login:code:";
public static final Long LOGIN_CODE_TTL = 2L;
public static final String LOGIN_USER_KEY = "login:token:";
public static final Long LOGIN_USER_TTL = 30L;
```

TTL 调用统一使用 `TimeUnit.MINUTES`。参考项目中的 `36000` 分钟是错误值，不能照搬。

## 任务 1：实体和 DTO

创建/调整：

- `entity/User`：`@Data @NoArgsConstructor @AllArgsConstructor`，字段对应 `tb_user`。
- `dto/LoginFormDTO`：phone、code。
- `dto/UserDTO`：只保存 id、nickName、icon，不保存 phone、password。
- `mapper/UserMapper extends BaseMapper<User>`。
- `IUserService extends IService<User>`。

登录 DTO：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginFormDTO {
    private String phone;
    private String code;
}
```

## 任务 2：发送验证码

Controller：

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }
}
```

接口：

```java
Result sendCode(String phone);
```

Service 核心代码：

```java
@Override
public Result sendCode(String phone) {
    if (RegexUtils.isPhoneInvalid(phone)) {
        return Result.fail("手机号格式错误");
    }

    String code = RandomUtil.randomNumbers(6);
    stringRedisTemplate.opsForValue().set(
            RedisConstants.LOGIN_CODE_KEY + phone,
            code,
            RedisConstants.LOGIN_CODE_TTL,
            TimeUnit.MINUTES
    );
    return Result.ok();
}
```

需要 `cn.hutool.core.util.RandomUtil`。验证码不返回前端，也不写普通日志；本地学习时使用 `redis-cli GET` 查看。

验证：

```bash
curl -X POST 'http://localhost:8081/user/code?phone=13912345678'
docker exec docker-redis-1 redis-cli GET login:code:13912345678
docker exec docker-redis-1 redis-cli TTL login:code:13912345678
```

## 任务 3：登录并保存 Token

接口：

```java
Result login(LoginFormDTO loginForm);
```

Controller：

```java
@PostMapping("/login")
public Result login(@RequestBody LoginFormDTO loginForm) {
    return userService.login(loginForm);
}
```

Service 顺序：

1. 校验 phone、code。
2. 从 `login:code:{phone}` 读取验证码。
3. 验证失败返回错误；验证成功删除验证码，防止重复使用。
4. 按 phone 查询 User；不存在则创建随机昵称用户。
5. `UUID.randomUUID().toString(true)` 生成 Token。
6. `BeanUtil.copyProperties(user, UserDTO.class)` 转为 UserDTO。
7. `BeanUtil.beanToMap` 转为字符串 Map，写入 Redis Hash。
8. 设置 30 分钟 TTL，返回 Token。

核心转换：

```java
UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
Map<String, Object> userMap = BeanUtil.beanToMap(
        userDTO,
        new HashMap<>(),
        CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((name, value) -> value.toString())
);
```

写入 Token：

```java
String token = UUID.randomUUID().toString(true);
String tokenKey = LOGIN_USER_KEY + token;
stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
return Result.ok(token);
```

`tb_user.phone` 必须有唯一索引。当前按参考项目方式完成查建用户；并发注册测试时如果出现唯一键冲突，再在本步骤修正为冲突后重新查询。

## 任务 4：双拦截器

创建：

- `UserHolder`：ThreadLocal 保存 UserDTO。
- `RefreshTokenInterceptor`：拦截所有请求，读取 Authorization、查询 Redis Hash、恢复 UserHolder、刷新 30 分钟 TTL。
- `LoginInterceptor`：只检查 UserHolder，没有用户时返回 401。
- `MvcConfig`：Refresh order=0，Login order=1；公开 code、login 和公开读接口。

RefreshTokenInterceptor 的 `afterCompletion` 必须调用：

```java
UserHolder.removeUser();
```

ThreadLocal 属于复用的 Web 线程，不清理可能把上一次请求的用户带到下一次请求。

## 任务 5：me 与 logout

```java
@GetMapping("/me")
public Result me() {
    return Result.ok(UserHolder.getUser());
}

@PostMapping("/logout")
public Result logout(@RequestHeader("Authorization") String token) {
    return userService.logout(token);
}
```

退出删除：

```java
stringRedisTemplate.delete(LOGIN_USER_KEY + token);
UserHolder.removeUser();
return Result.ok();
```

参考项目只清 ThreadLocal 不能让 Token 失效，因此这里直接修正为删除 Redis Token。

## 邮箱登录扩展

手机号主线完成后再参考 `dianping_STMP`：

1. 单独增加 email 字段和唯一索引，不能把邮箱塞入长度 11 的 phone 字段。
2. 使用 `RegexUtils.isEmailInvalid` 校验邮箱。
3. 使用 JavaMail/QQ SMTP 发送验证码，验证码仍放 Redis，登录成功后的 Token 和拦截器完全复用。
4. SMTP 用户名、授权码从配置读取，不能复制参考项目中的硬编码账号和授权码。
5. 当前先完成同步邮件发送；异步、分级限流作为后续优化，不放入手机号登录主线。

邮件接口同样不使用 HttpSession：

```java
Result sendEmailCode(String email);
Result loginByEmail(EmailLoginFormDTO loginForm);
```

## 完成标准

- [ ] 所有登录方法都没有 HttpSession 参数和 import。
- [ ] 手机验证码是 6 位数字，Redis TTL 不超过 120 秒。
- [ ] 登录成功删除验证码，返回 Token。
- [ ] Token Hash 只保存 UserDTO，TTL 为 30 分钟。
- [ ] Refresh 拦截器能恢复用户和续期，Login 拦截器返回 401。
- [ ] 请求结束清理 ThreadLocal，logout 删除 Redis Token。
- [ ] 邮箱扩展关闭时不影响手机号登录。

[上一课](0001-foundation.md)｜[下一课](0003-shop-cache.md)。
