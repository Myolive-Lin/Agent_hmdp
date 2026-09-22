package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPersistService {

    private final IVoucherOrderService voucherOrderService;
    private final ISeckillVoucherService seckillVoucherService;

    public OrderPersistService(
            IVoucherOrderService voucherOrderService,
            ISeckillVoucherService seckillVoucherService) {
        this.voucherOrderService = voucherOrderService;
        this.seckillVoucherService = seckillVoucherService;
    }

    @Transactional
    public void createOrder(VoucherOrder order) {
        if (voucherOrderService.getById(order.getId()) != null) {
            return;
        }

        int existing = voucherOrderService.query()
                .eq("user_id", order.getUserId())
                .eq("voucher_id", order.getVoucherId())
                .count();
        if (existing > 0) {
            return;
        }

        //seckill中扣除stock
        boolean deducted = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!deducted) {
            throw new IllegalStateException(
                    "MySQL 库存扣减失败，voucherId="
                            + order.getVoucherId());
        }

        if (!voucherOrderService.save(order)) {
            throw new IllegalStateException(
                    "订单保存失败，orderId=" + order.getId());
        }
    }
}