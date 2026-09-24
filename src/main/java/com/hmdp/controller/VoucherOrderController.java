package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    //根据Voucher id来进行消费,然后放到任务队列中
    private final IVoucherOrderService voucherOrderService;

    /**
     * 用户发起秒杀请求。
     * 成功只表示请求已进入 RabbitMQ，
     * 订单随后由消费者异步写入 MySQL。
     */
    //@PathVariable 本身就是URL路径的一部分
    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long id){
        return voucherOrderService.seckillVoucher(id);
    }

    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long id){
        return voucherOrderService.queryOrderById(id);

    }
}
