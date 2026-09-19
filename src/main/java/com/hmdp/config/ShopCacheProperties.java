package com.hmdp.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "hmdp.cache")
public class ShopCacheProperties {

    private Set<Long> hotShopIds = new HashSet<Long>();

    public Set<Long> getHotShopIds(){
        return hotShopIds;
    }

    public void setHotShopIds(Set<Long> hotShopIds){
        this.hotShopIds = hotShopIds;
    }

    public boolean isHotShop(Long shopId){
        return shopId != null && hotShopIds.contains(shopId);
    }
}
