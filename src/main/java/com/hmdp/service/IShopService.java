package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商铺
     * @param shop
     * @return
     */
    Result updateShop(Shop shop);

    /**
     * 互斥锁
     * @param id
     * @return
     */
    Result queryShopWithMutex(Long id);

    /**
     * 逻辑过期
     * @param id
     * @return
     */
    Result queryShopLogicalExpire(Long id);

    /**
     * 查询商铺分类信息,带坐标
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
