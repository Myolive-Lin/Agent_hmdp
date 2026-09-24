package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher")
public class VoucherController {
    public VoucherController(IVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    private final IVoucherService voucherService;

    @PostMapping("/seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher){
        return voucherService.addSeckillVoucher(voucher);
        //添加Voucher
    }

    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId){
        return voucherService.queryVoucherOfShop(shopId);
    }
}
