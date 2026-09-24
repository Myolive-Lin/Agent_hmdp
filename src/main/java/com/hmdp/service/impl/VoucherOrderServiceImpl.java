package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.config.SeckillRabbitConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    //用户发起请求后，系统校验优惠卷ID，登录状态，并做限流，然后查询秒杀券是否存在以及是否当前在活动时间内；校验后先生成一个全局唯一的订单ID
    //再执行lua脚本,原子地完成库存检查、一人一单判断、预扣库存和记录用户购买资格;如果成功就组装VoucherOrder订单对象发送到RabbitMQ，由消费者异步写入MySQL
    // 当前接口只返回orderId，表示秒杀请求已经被受理，而不是数据库订单已经落库

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final RateLimiter rateLimiter = RateLimiter.create(10.0); //每秒放10个许可

    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker, RabbitTemplate rabbitTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.rabbitTemplate = rabbitTemplate;
    }

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;

    private final RabbitTemplate rabbitTemplate;

    @Override
    public Result seckillVoucher(Long voucherID) {
        if (voucherID==null || voucherID <= 0){
            return Result.fail("优惠券ID不合法");
        }

        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null || userDTO.getId() == null){
            return Result.fail("请先登录");
        }

        if (!rateLimiter.tryAcquire()){
            return Result.fail("系统繁忙，请稍后再试");
        }

        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherID);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (!now.isBefore(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }

        Long orderId = redisIdWorker.nextID(RedisConstants.SECKILL_ORDER_KEY);

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherID.toString(),
                userDTO.getId().toString()
        );

        if (result == null){
            throw new IllegalStateException("秒杀脚本执行失败");
        }
        if(result == 1L){
            return Result.fail("库存不足");
        }

        if (result == 2L){
            return Result.fail("不能重复下单");
        }

        if(result != 0L){
            throw new IllegalStateException("未知秒杀结果：" + result);
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherID);
        voucherOrder.setUserId(userDTO.getId());

        rabbitTemplate.convertAndSend(
                SeckillRabbitConfig.EXCHANGE,
                SeckillRabbitConfig.ROUTING_KEY,
                voucherOrder
        );

        // 此时只代表被受理
        return Result.ok(orderId);
    }

    @Override
    public Result queryOrderById(Long orderId) {
        if (orderId == null || orderId <= 0){
            return Result.fail("订单ID不合法");
        }

        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null){
            return Result.fail("请先登录");
        }

        VoucherOrder order = getById(orderId);

        if (order == null){
            return Result.fail("订单不存在或正在处理中");
        }

        if(!user.getId().equals( order.getUserId())){
            return Result.fail("无权查询该订单");
        }
        return Result.ok(order);

    }
}
