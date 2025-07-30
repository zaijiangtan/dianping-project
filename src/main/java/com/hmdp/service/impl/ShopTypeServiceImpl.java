package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从缓存中查找
        String typeListStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST_KEY);

        // 2.如果存在则直接返回
        if (StrUtil.isNotBlank(typeListStr)) {
            List<ShopType> typeList = JSONUtil.toList(typeListStr, ShopType.class);
            return Result.ok(typeList);
        }

        // 3.不存在则从数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4.存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST_KEY, JSONUtil.toJsonStr(typeList));

        // 5.返回数据
        return Result.ok(typeList);
    }
}
