package com.hmdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisHandler implements InitializingBean {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopService shopService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化缓存
        // 1.查询商品信息
        List<Shop> shopList = shopService.list();
        // 2.放入缓存
        for (Shop shop : shopList) {
            // 2.1.item序列化为JSON
            String json = MAPPER.writeValueAsString(shop);
            // 2.2.存入redis
            redisTemplate.opsForValue().set("item:id:" + shop.getId(), json);
        }

        // 3.查询商品库存信息
        //List<ItemStock> stockList = stockService.list();
        // 4.放入缓存
//        for (ItemStock stock : stockList) {
//            // 2.1.item序列化为JSON
//            String json = MAPPER.writeValueAsString(stock);
//            // 2.2.存入redis
//            redisTemplate.opsForValue().set("item:stock:id:" + stock.getId(), json);
//        }
    }
}
