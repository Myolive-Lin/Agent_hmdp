package com.hmdp.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_shop")
public class Shop implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long typeId;

    private String images;

    private String area;

    private String address;

    private Double x;

    private Double y;

    //均价，
    private  Long avgPrice;

    private Integer sold;

    private Integer comments;

    // 评分乘以 10 保存。例如 37 代表 3.7 分
    private Integer score;

    private String openHours;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private Double distance;






}
