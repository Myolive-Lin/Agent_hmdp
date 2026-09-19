package com.hmdp.utils;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import static cn.hutool.core.text.CharSequenceUtil.bytes;

@Component
public class ShopBloomFilter {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 过滤器不存在时候创建
     *  0.001：约0.1%的假阳性率
     *  100000： 预计最多容纳 10万商户 ID
     *  Bloom 返回 false：一定不存在
     *  Bloom 返回 true：可能存在，必须查缓存和数据库。
     */
    public void createIfAbsent(){
        Boolean exists = stringRedisTemplate.hasKey(RedisConstants.SHOP_BLOOM_KEY);

        if (Boolean.TRUE.equals(exists)){
            return;
        }
        stringRedisTemplate.execute(
                (RedisCallback<Object>) connection -> connection.execute(
                        "BF.RESERVE",
                        bytes(RedisConstants.SHOP_BLOOM_KEY),
                        bytes("0.001"),
                        bytes("100000")
                )
        );
    }


    public void addAll(Collection<Long> shopIds){
        if (shopIds == null || shopIds.isEmpty()){
            return;
        }

        String[] ids = new String[shopIds.size()];

        int index = 0;
        for (Long shopId: shopIds){
            ids[index++] = String.valueOf(shopId);
        }

        stringRedisTemplate.execute(
                ADD_ALL_SCRIPT,
                Collections.singletonList(RedisConstants.SHOP_BLOOM_KEY), ids
        );
    }



    /*
     * 新建商户成功后写入 Bloom。
     */
    public void add(Long shopId) {
        addAll(Collections.singletonList(shopId));
    }

    public boolean mightContain(Long shopId) {
        Long result = stringRedisTemplate.execute(
                EXISTS_SCRIPT,
                Collections.singletonList(RedisConstants.SHOP_BLOOM_KEY),
                String.valueOf(shopId)
        );

        return Long.valueOf(1L).equals(result);
    }

    private byte[] bytes(Object value){
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static final DefaultRedisScript<Long> ADD_ALL_SCRIPT =
            new DefaultRedisScript<Long>(
                    "for i = 1, #ARGV do "
                            + "redis.call('BF.ADD', KEYS[1], ARGV[i]) "
                            + "end "
                            + "return #ARGV",
                    Long.class
            );

    private static final DefaultRedisScript<Long> EXISTS_SCRIPT =
            new DefaultRedisScript<Long>(
                    "return redis.call('BF.EXISTS', KEYS[1], ARGV[1])",
                    Long.class
            );

}
