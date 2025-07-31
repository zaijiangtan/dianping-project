package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将数据转为json存入redis，并添加ttl
     */
    public void setWithExpire (String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 将数据和过期时间存入redisData，后转为json存入redis
     */
    public void setWithLogicalExpire (String key, Object value, Long time, TimeUnit timeUnit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存空对象解决缓存穿透
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                          Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        // 1.从redis查询商户信息
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断商户信息是否存在
        if (StrUtil.isNotBlank(json)) {
            // 2.1.存在则直接返回商户信息
            return JSONUtil.toBean(json, type);
        }

        // 3.判断命中的是否是空值
        if (json != null) { // 不等于null说明是空字符串-实现缓存空对象
            // 3.1是则返回错误信息（数据在数据库中也不存在）
            return null;
        }

        // 4.不存在则从数据库读取
        R r = dbFallback.apply(id);

        // 5.数据库不存在则返回错误信息
        if(r == null){
            // 5.1将空值写入redis缓存
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6.存在则存入redis
        this.setWithExpire(key, r, time, timeUnit);

        // 7.返回数据
        return r;
    }

    /**
     * 互斥锁解决缓存击穿
     */
    public <R, ID> R queryWithMutex (String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                     Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        // 1.从redis查询商户信息
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断商户信息是否存在
        if (StrUtil.isNotBlank(json)) {
            // 2.1.存在则直接返回商户信息
            return JSONUtil.toBean(json, type);
        }

        // 3.判断命中的是否是空值
        if (json != null) { // 不等于null说明是空字符串-实现缓存空对象
            // 3.1是则返回错误信息（数据在数据库中也不存在）
            return null;
        }

        // 4.实现缓存重建-解决缓存击穿
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;

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
                // 4.2.获取锁失败，休眠一段时间重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            // 4.3.获取锁后再次判断缓存-double-check
            // 1.从redis查询商户信息
            json = stringRedisTemplate.opsForValue().get(key);

            // 2.判断商户信息是否存在
            if (StrUtil.isNotBlank(json)) {
                // 2.1.存在则直接返回商户信息
                return JSONUtil.toBean(json, type);
            }

            // 3.判断命中的是否是空值
            if (json != null) { // 不等于null说明是空字符串-实现缓存空对象
                // 3.1是则返回错误信息（数据在数据库中也不存在）
                return null;
            }

            // 4.4.获取锁后，从数据库读取
            r = dbFallback.apply(id);
            // 模拟重建
            //Thread.sleep(300);

            // 5.数据库不存在则返回错误信息
            if(r == null){
                // 5.1将空值写入redis缓存
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6.存在则存入redis
            this.setWithExpire(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放锁
            unLock(lockKey);
        }

        // 8.返回数据
        return r;
    }

    /**
     * 逻辑过期解决缓存击穿问题
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                            Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.从redis查询商户信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 2.1.不存在则返回空
            return null;
        }

        // 3.存在则检查是否过期
        // 3.1.将redis的json反序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 3.2.获取对象
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(jsonObject, type);
        // 3.3判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.4没过期，直接返回店铺信息
            return r;
        }

        // 4.过期则需要重建
        // 4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = getLock(lockKey);
        // 4.2.判断是否获取锁成功
        if (isLock) {
            // 4.3.获取成功后，需要再次判断缓存是否过期，做double check
            // 3.1.将redis的json反序列化成对象
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            // 3.2.获取shop对象
            jsonObject = (JSONObject) redisData.getData();
            r = JSONUtil.toBean(jsonObject, type);
            // 3.3判断是否过期
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                return r;
            }

            // 4.4开启独立线程，进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newR, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 4.5释放锁
                    unLock(lockKey);
                }
            });
        }
        // 5.失败则返回过期数据
        return r;
    }

    private boolean getLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
