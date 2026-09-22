package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tb_voucher_order")
public class VoucherOrder {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    private Integer status;
}
