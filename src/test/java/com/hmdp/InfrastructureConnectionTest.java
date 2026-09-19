package com.hmdp;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InfrastructureConnectionTest{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldConnectToMysql(){
        Integer result  = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );
    }
    @Test
    void shouldWriteAndReadRedis(){
        String key = "test:conection:" + UUID.randomUUID();

        try{
            redisTemplate.opsForValue().set(
                    key,
                    "connected",
                    1,
                    TimeUnit.MINUTES
            );

            String value = redisTemplate.opsForValue().get(key);
            assertEquals("connected", value);
        } finally {
            redisTemplate.delete(key);
        }
    }
}