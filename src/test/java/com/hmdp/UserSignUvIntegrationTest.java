package com.hmdp;

import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class UserSignUvIntegrationTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 验证 Bitmap 连续签到计算。
     *
     * 模拟本月前四天：
     * 第 1 天：签到
     * 第 2 天：未签到
     * 第 3 天：签到
     * 第 4 天：签到
     *
     * 从第 4 天向前连续签到为 2 天。
     */
    @Test
    public void shouldCountContinuousSignDays() {
        String key = RedisConstants.USER_SIGN_KEY
                + "test:"
                + UUID.randomUUID().toString().replace("-", "");

        try {
            // 第 1 天、3 天、4 天签到
            stringRedisTemplate.opsForValue().setBit(key, 0, true);
            stringRedisTemplate.opsForValue().setBit(key, 2, true);
            stringRedisTemplate.opsForValue().setBit(key, 3, true);

            List<Long> result = stringRedisTemplate.opsForValue()
                    .bitField(
                            key,
                            BitFieldSubCommands.create()
                                    .get(
                                            BitFieldSubCommands.BitFieldType
                                                    .unsigned(4)
                                    )
                                    .valueAt(0)
                    );

            assertNotNull(result);
            assertTrue(!result.isEmpty());

            long num = result.get(0);
            int count = 0;

            while ((num & 1L) == 1L) {
                count++;
                num >>>= 1;
            }

            // 第 4 天和第 3 天连续签到；第 2 天断签
            assertEquals(2, count);
        } finally {
            // 只删除当前测试创建的 Key
            stringRedisTemplate.delete(key);
        }
    }

    /**
     * 验证 HyperLogLog 对重复访客去重。
     *
     * HyperLogLog 是近似统计，因此使用误差范围断言。
     */
    @Test
    public void shouldEstimateDailyUvAndDeduplicateVisitors() {
        String key = RedisConstants.UV_KEY
                + "test:"
                + UUID.randomUUID().toString().replace("-", "");

        try {
            // 写入 1000 个不同访客
            for (int i = 0; i < 1000; i++) {
                stringRedisTemplate.opsForHyperLogLog()
                        .add(key, "visitor-" + i);
            }

            // 重复访问不应使 UV 按访问次数增加
            for (int i = 0; i < 100; i++) {
                stringRedisTemplate.opsForHyperLogLog()
                        .add(key, "visitor-" + i);
            }

            Long estimate = stringRedisTemplate.opsForHyperLogLog()
                            .size(key);

            assertNotNull(estimate);

            /*
             * HLL 是近似值。
             * 1000 访客通常误差远小于 2%，这里允许 980～1020。
             */
            assertTrue(
                    estimate >= 980L && estimate <= 1020L,
                    "UV 估算值超出允许误差范围：" + estimate
            );
        } finally {
            stringRedisTemplate.delete(key);
        }
    }
}