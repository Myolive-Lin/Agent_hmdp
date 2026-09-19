package com.hmdp.controller;


import com.hmdp.dto.LoginFromDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone){
        return userService.send_code(phone);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFromDTO loginFromDTO){//从Body中提取并转换为loginFromDTO对象
        return userService.login(loginFromDTO);
    }

    @GetMapping("/me")
    public Result me() {
        // RefreshTokenInterceptor 已经把当前用户保存进 ThreadLocal
        return Result.ok(UserHolder.getUser());
    }

    @PostMapping("logout")
    public Result logout(@RequestHeader("Authorization") String token){
        return userService.logout(token);
    }

}
