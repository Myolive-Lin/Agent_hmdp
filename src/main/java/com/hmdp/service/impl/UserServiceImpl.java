package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFromDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import cn.hutool.core.lang.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RegexUtils.isPhoneInvalid;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result send_code(String phone) {
        //检验手机号
        if(isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送登录验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFromDTO loginFrom) {
        //登录逻辑，先验证手机号码、验证码是否正确，就用手机号去数据库中查找用户，如果不存在就注册一下
        //注册后再生成一个token，作为用户后续访问的凭证，将User转换成必要的UserDTO格式，再以token为Key，DTO信息为value存入Hash中。
        //并设置登录有效期，最后删除已经使用的验证码，并把Token传给前端。
        //之后只需要通过 Token -> Redis-> UserDTO,找到当前用户信息
        String code = loginFrom.getCode();
        String phone = loginFrom.getPhone();

        // 1. Service 再次校验，不能因为发码时校验过就信任登录参数
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }

        String codeKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String saveCode = stringRedisTemplate.opsForValue().get(codeKey);

        if (saveCode == null || !saveCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        //按手机号查询当前的用户
        User user = query().eq("phone",phone).one();

        //判断用户是否存在
        if(user == null){
            user = createWithPhone(phone);
        }

        //5. 生成随机 Token
        String token = UUID.randomUUID().toString(true);

        // 6. 只保留登录需要的信息，避免把密码放进 Redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 7. 把 UserDTO 对象转换成 Map，并把里面所有非空字段的值统一转换成字符串，方便存入 Redis Hash。

        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
        CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((name,value) -> value.toString()) //value都换成字符串
        );

        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(
                tokenKey,
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );
        //登录后删除验证码，防止重复使用
        stringRedisTemplate.delete(codeKey);
        //返还token给前端
        return Result.ok(token);


    }

    @Override
    public Result logout(String token) {
        // 删除 Redis 登录态，后续请求无法再通过此 Token 登录
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY+token);
        UserHolder.removeUser();
        return Result.ok();
    }

    private User createWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);

        // 默认昵称由服务端生成，不要求用户首次登录填写资料
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save((user));
        return user;
    }



}
