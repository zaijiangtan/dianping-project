package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithMutex(id);
        // Shop shop = queryWithPassThrough(id); //缓存穿透

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithMutex (Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        // 2.存在则直接返回商户信息
        if (StrUtil.isNotBlank(shopStr)) {
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return shop;
        }

        if (shopStr != null) { // 不等于null说明是空字符串-实现缓存空对象
            return null;
        }

        // 3.实现缓存重建-解决缓存击穿
        // 3.1.不存在则先尝试能否获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;

        /*int retry = 0; // 避免栈溢出
        while (!getLock(lockKey)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            retry++;
            if (retry > 3) {
                return null;
            }
        }*/
        try {
            if (!getLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 3.2.获取锁后再次判断缓存
            // 1.从redis查询商户信息
            shopStr = stringRedisTemplate.opsForValue().get(key);
            // 2.存在则直接返回商户信息
            if (StrUtil.isNotBlank(shopStr)) {
                shop = JSONUtil.toBean(shopStr, Shop.class);
                return shop;
            }

            if (shopStr != null) { // 不等于null说明是空字符串-实现缓存空对象
                return null;
            }

            // 从数据库读取
            shop = getById(id);
            // 模拟重建
            // Thread.sleep(200);

            // 4.数据库不存在则返回错误信息
            if(shop == null){
                // 4.1将空值写入redis缓存
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 5.存在则存入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }

        // 6.返回数据
        return shop;
    }

    private boolean getLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户信息
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        // 2.存在则直接返回商户信息
        if (StrUtil.isNotBlank(shopStr)) {
            Shop shop = JSONUtil.toBean(shopStr, Shop.class);
            return shop;
        }

        if (shopStr != null) { // 不等于null说明是空字符串-实现缓存空对象
            return null;
        }

        // 3.不存在则从数据库读取
        Shop shop = getById(id);

        // 4.数据库不存在则返回错误信息
        if(shop == null){
            // 4.1将空值写入redis缓存
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5.存在则存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6.返回数据
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.修改数据库中的商户数据
        updateById(shop);

        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
