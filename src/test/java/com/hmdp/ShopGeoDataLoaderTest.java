package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class ShopGeoDataLoaderTest {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将数据库商户按 typeId 分组，批量导入 Redis GEO。
     */
    @Test
    public void loadShopGeoData() {
        List<Shop> shops = shopService.list();

        Map<Long, List<Shop>> shopMap = shops.stream()
                .filter(shop -> shop.getTypeId() != null)
                .filter(shop -> shop.getX() != null && shop.getY() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();

            String key = RedisConstants.SHOP_GEO_KEY + typeId;

            // 重跑初始化时先删除旧坐标，防止残留数据。
            stringRedisTemplate.delete(key);

            List<RedisGeoCommands.GeoLocation<String>> locations =
                    new ArrayList<RedisGeoCommands.GeoLocation<String>>();

            for (Shop shop : shopList) {
                locations.add(
                        new RedisGeoCommands.GeoLocation<String>(
                                shop.getId().toString(),
                                new Point(shop.getX(), shop.getY())
                        )
                );
            }

            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}