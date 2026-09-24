package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_blog")
public class Blog {

    @TableId(type = IdType.AUTO, value = "id")
    private Long id;

    private Long userId;
    private Long shopId;

    /*
     * 以下三个字段不属于 tb_blog。
     * 查询笔记时由用户信息和 Redis 点赞状态补充。
     */
    @TableField(exist = false)
    private String icon;

    @TableField(exist = false)
    private String name;

    @TableField(exist = false)
    private Boolean isLike;

    private String title;

    /*
     * 多张图片用英文逗号分隔：
     * /imgs/blogs/a.jpg,/imgs/blogs/b.jpg
     */
    private String images;

    private String content;

    private Integer liked;

    private Integer comments;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
