package com.hmdp.dto;

/*
 * 热点商户不会直接把 Shop 写入 Redis。
 *
 * Redis 中保存的是：
 * {
 *   "expireTime": "2026-...",
 *   "data": { 商户数据 }
 * }
 *
 * expireTime 是“逻辑过期时间”，不是 Redis Key 的物理 TTL。
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
