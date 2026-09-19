package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

        // 从这里开始建立Spring 容器、自动配置并扫描组件
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class DianpingLearningApplication {
    public static void main(String []args){
        SpringApplication.run(
                DianpingLearningApplication.class,
                    args

        );
    }
}
