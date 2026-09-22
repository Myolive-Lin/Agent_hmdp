package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    // 先把优惠卷信息和秒杀信息写入MySQL，事务提交成功后，再把秒杀库存写入Redis
    private final StringRedisTemplate stringRedisTemplate;
    private final ISeckillVoucherService seckillVoucherService;

    public VoucherServiceImpl(
            ISeckillVoucherService seckillVoucherService,
            StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public Result addSeckillVoucher(Voucher voucher) {
        if (voucher == null
                || voucher.getShopId() == null
                || voucher.getTitle() == null
                || voucher.getPayValue() == null
                || voucher.getActualValue() == null
                || voucher.getStock() == null
                || voucher.getStock() <= 0
                || voucher.getBeginTime() == null
                || voucher.getEndTime() == null
                || !voucher.getBeginTime()
                .isBefore(voucher.getEndTime())) {
            return Result.fail("秒杀券参数不完整");
        }

        voucher.setType(1);
        voucher.setStatus(1);

        if (!save(voucher)){
            throw new IllegalStateException("保存优惠券失败");
        }

        SeckillVoucher seckill = new SeckillVoucher();
        seckill.setVoucherId(voucher.getId());
        seckill.setStock(voucher.getStock());
        seckill.setBeginTime(voucher.getBeginTime());
        seckill.setEndTime(voucher.getEndTime());

        if (!seckillVoucherService.save(seckill)) {
            throw new IllegalStateException("保存秒杀信息失败");
        }

        final Long voucherId = voucher.getId();
        final String stock = voucher.getStock().toString();

        // MySQL提交成功后，才发布Redis库存
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.opsForValue().set(
                                RedisConstants.SECKILL_STOCK_KEY + voucherId,
                                stock
                        );
                    }
                }
        );

        return Result.ok(voucherId);
    }
}
