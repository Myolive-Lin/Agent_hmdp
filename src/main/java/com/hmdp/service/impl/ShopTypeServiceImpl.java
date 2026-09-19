package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/*
 * 分类数据变化很少，使用Redis List
 * MySQL查询时 按照sort升序；
 * Redis写入时候使用 rightPushAll;
 * Redis读取时 从 0 到 - 1；
 * 因此读取舒心与sort顺序保持一致；
 */


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result querySort() {
        //先从Redis中进行查找，如果没有再去从MySQL中进行查找
        List<String> cachedJsonList = stringRedisTemplate.opsForList().range(
                RedisConstants.SHOP_TYPE_KEY,
                0,
                -1
        );

        //Cache命中，直接反序列化返回
        if(cachedJsonList != null && !cachedJsonList.isEmpty()){
            List<ShopType> shopTypes = cachedJsonList.stream().map(
                    json -> JSONUtil.toBean(json, ShopType.class)
            ).collect(Collectors.toList());

            return Result.ok(shopTypes);
        }

        //redis 未命中，查询SQL
        List<ShopType> shopTypes =  query().orderByAsc("sort").list();

        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("没有分类数据");
        }

        List<String> jsonList = new ArrayList<String>();
        for (ShopType shopType : shopTypes){
            jsonList.add(JSONUtil.toJsonStr(shopType));
        }

        /*
         * 不能使用 leftPushAll：
         * LPUSH 会反转列表顺序。
         */
        stringRedisTemplate.opsForList().rightPushAll(
                RedisConstants.SHOP_TYPE_KEY,
                jsonList
                );
        return Result.ok(shopTypes);
    }
}
