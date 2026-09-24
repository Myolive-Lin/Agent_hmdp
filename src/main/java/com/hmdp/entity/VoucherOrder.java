package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher_order")
public class VoucherOrder {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    /*
     * 订单状态：
     * 1 未支付
     * 2 已支付
     * 3 已核销
     * 4 已取消
     * 5 退款中
     * 6 已退款
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
