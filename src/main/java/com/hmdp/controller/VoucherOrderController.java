package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    //根据Voucher id来进行消费,然后放到任务队列中
    private final IVoucherOrderService voucherOrderService;


    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long id){
        return voucherOrderService.seckillVoucher(id);
    }
}
