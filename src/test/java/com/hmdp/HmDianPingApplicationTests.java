package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void queryHotShopTest() throws InterruptedException {
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 1L, TimeUnit.SECONDS);
    }

    @Test
    public void getToken() throws IOException {
        int pageSize = 100;
        int total = 1000;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("tokens.txt"))) {
            for (int page = 1; page <= total / pageSize; page++) {
                // 分页查询用户
                Page<User> userPage = userService.page(new Page<>(page, pageSize));
                List<User> users = userPage.getRecords();

                for (User user : users) {
                    // 生成token
                    String token = UUID.randomUUID().toString(true);

                    // 转换为DTO
                    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                            CopyOptions.create()
                                    .setIgnoreNullValue(true)
                                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));

                    // 存入Redis
                    String tokenKey = LOGIN_USER_KEY + token;
                    stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                    stringRedisTemplate.expire(tokenKey, 5L, TimeUnit.HOURS);

                    // 写入文件
                    writer.write(token);
                    writer.newLine();
                }
            }
        }
    }


}
