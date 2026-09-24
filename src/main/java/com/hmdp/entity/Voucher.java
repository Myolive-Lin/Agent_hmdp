package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher")
public class Voucher {
    @TableId(value = "id", type= IdType.AUTO)
    private Long id;

    private Long shopId;
    private String title;
    private String subTitle;
    //使用规则
    private String rules;
    private Long payValue;
    private Long actualValue;
    // 优惠券类型：0 普通券，1 秒杀券
    private Integer type;
    // 状态：1 正常，2 下架，3 过期
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 这三个字段属于 tb_seckill_voucher
    @TableField(exist = false)
    private Integer stock;

    @TableField(exist = false)
    private LocalDateTime beginTime;

    @TableField(exist = false)
    private LocalDateTime endTime;



}
