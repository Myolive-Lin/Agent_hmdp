package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFromDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;


// IService 提供 MyBatis-Plus 通用 CRUD 能力
public interface IUserService extends IService<User> {
    Result send_code(String phone);

    Result login(LoginFromDTO loginFrom);

    Result logout(String token);
}
