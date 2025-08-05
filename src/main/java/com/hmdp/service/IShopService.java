package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商户
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 修改商户信息
     * @param shop
     * @return
     */
    Result updateShop(@RequestBody Shop shop);

    /**
     * 按类型和距离查询店铺
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
