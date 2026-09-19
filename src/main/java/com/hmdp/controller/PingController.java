package com.hmdp.controller;

//RestController 处理Http请求的Controller，把他注册到Spring

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
    @GetMapping("/ping")
    public String ping(){
        return "pong";
    }
}
