package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {
    Result addSeckillVoucher(Voucher voucher);
}
