package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VoucherModuleIntegrationTest {

    @Autowired
    private IVoucherService voucherService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long voucherId;
    private Long orderId;

    @Test
    void shouldPublishAndConsumeSeckillOrder()
            throws Exception {

        /*
         * 1. 发布一张库存为 1 的秒杀券
         */
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("第四课集成测试秒杀券");
        voucher.setPayValue(100L);
        voucher.setActualValue(1000L);
        voucher.setStock(1);
        voucher.setBeginTime(
                LocalDateTime.now().minusMinutes(1));
        voucher.setEndTime(
                LocalDateTime.now().plusMinutes(10));

        Result publishResult =
                voucherService.addSeckillVoucher(voucher);

        assertTrue(
                Boolean.TRUE.equals(publishResult.getSuccess()),
                "发布秒杀券失败：" + publishResult.getErrorMsg()
        );

        assertNotNull(publishResult.getData());

        voucherId =
                ((Number) publishResult.getData()).longValue();

        /*
         * 2. 检查 MySQL 秒杀信息
         */
        SeckillVoucher seckillVoucher =
                seckillVoucherService.getById(voucherId);

        assertNotNull(seckillVoucher);
        assertEquals(1, seckillVoucher.getStock());

        /*
         * 3. 检查 Redis 库存
         */
        String redisStock =
                redisTemplate.opsForValue().get(
                        RedisConstants.SECKILL_STOCK_KEY
                                + voucherId
                );

        assertEquals("1", redisStock);

        /*
         * 4. 模拟已登录用户
         */
        UserDTO user = new UserDTO();
        user.setId(900001L);
        user.setNickName("seckill-test-user");

        UserHolder.setTl(user);

        /*
         * 5. 发起秒杀
         */
        Result seckillResult;

        try {
            seckillResult =
                    voucherOrderService
                            .seckillVoucher(voucherId);
        } finally {
            UserHolder.removeUser();
        }

        assertTrue(
                Boolean.TRUE.equals(seckillResult.getSuccess()),
                "秒杀失败：" + seckillResult.getErrorMsg()
        );

        assertNotNull(seckillResult.getData());

        orderId =
                ((Number) seckillResult.getData()).longValue();

        /*
         * 6. 等待 RabbitMQ 异步消费者写入订单
         */
        VoucherOrder order =
                waitForOrder(orderId, 5000L);

        assertNotNull(order);
        assertEquals(orderId, order.getId());
        assertEquals(900001L, order.getUserId());
        assertEquals(voucherId, order.getVoucherId());

        /*
         * 7. 检查 Redis 库存已经预扣为 0
         */
        String finalRedisStock =
                redisTemplate.opsForValue().get(
                        RedisConstants.SECKILL_STOCK_KEY
                                + voucherId
                );

        assertEquals("0", finalRedisStock);

        /*
         * 8. 检查 MySQL 库存已经扣减为 0
         */
        SeckillVoucher finalSeckill =
                seckillVoucherService.getById(voucherId);

        assertNotNull(finalSeckill);
        assertEquals(0, finalSeckill.getStock());

        /*
         * 9. 检查 Redis 已经记录购买用户
         */
        Boolean isMember =
                redisTemplate.opsForSet().isMember(
                        RedisConstants.SECKILL_ORDER_KEY
                                + voucherId,
                        "900001"
                );

        assertTrue(Boolean.TRUE.equals(isMember));

        /*
         * 10. 检查数据库订单数量
         */
        int orderCount = voucherOrderService.query()
                .eq("voucher_id", voucherId)
                .count();

        assertEquals(1, orderCount);
    }

    /**
     * 等待 RabbitMQ 异步消费完成。
     */
    private VoucherOrder waitForOrder(
            Long targetOrderId,
            long timeoutMillis)
            throws InterruptedException {

        long deadline =
                System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            VoucherOrder order =
                    voucherOrderService.getById(targetOrderId);

            if (order != null) {
                return order;
            }

            Thread.sleep(100L);
        }

        fail("等待 RabbitMQ 消费超时，orderId="
                + targetOrderId);

        return null;
    }

    /**
     * 测试完成后清理 MySQL 和 Redis。
     */
    @AfterEach
    void cleanUp() {
        UserHolder.removeUser();

        if (orderId != null) {
            voucherOrderService.removeById(orderId);
        }

        if (voucherId == null) {
            return;
        }

        // 防止测试中途失败后留下订单
        voucherOrderService.remove(
                new QueryWrapper<VoucherOrder>()
                        .eq("voucher_id", voucherId)
        );

        seckillVoucherService.removeById(voucherId);
        voucherService.removeById(voucherId);

        redisTemplate.delete(
                RedisConstants.SECKILL_STOCK_KEY
                        + voucherId
        );

        redisTemplate.delete(
                RedisConstants.SECKILL_ORDER_KEY
                        + voucherId
        );
    }
}
