package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFromDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ResultTreeType;


// IService 提供 MyBatis-Plus 通用 CRUD 能力
public interface IUserService extends IService<User> {
    Result send_code(String phone);

    Result login(LoginFromDTO loginFrom);

    Result logout(String token);

    /**
     * 当前用户今日签到。
     */
    Result sign();

    /**
     * 查询当前用户截至今天的连续签到天数。
     */
    Result signCount();
}
