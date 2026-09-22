package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextID(String keyPrefix) {
        long timestamp = Instant.now().getEpochSecond() - BEGIN_TIMESTAMP;

        String day = LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "icr:" + keyPrefix + ":" + day;
        Long sequence = stringRedisTemplate.opsForValue().increment(key);

        if (sequence == null) {
            throw new IllegalStateException("生成订单ID失败");
        }
        if (sequence == 1L) {
            stringRedisTemplate.expire(key, 2, TimeUnit.DAYS);
        }
        if (sequence > 0xffffffffL) {
            throw new IllegalStateException("当日序列已用尽");
        }

        return (timestamp << COUNT_BITS) | sequence;
    }
}
