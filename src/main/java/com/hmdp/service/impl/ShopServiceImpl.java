package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.resource.StringResource;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopMapper shopMapper;

    /**
     * 根据id查询商铺 [带缓存]
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1.先查询缓存
        String jsonShopObject = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否为空
        if (StrUtil.isNotBlank(jsonShopObject)){
            // 3.将json数据转换成Java对象
            Shop shop = JSONUtil.toBean(jsonShopObject, Shop.class);

            return Result.ok(shop);
        }
        // 4.如果缓存中没有,则从数据库中查询出来并重设缓存

        Shop DBShop = shopMapper.selectById(id);

        // 如果数据库没有则返回错误信息
        if (DBShop == null){
            return Result.fail("店铺不存在");
        }

        // 5.将DBShop存入缓存中

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(DBShop));

        // 设置过期时间

        stringRedisTemplate.expire(key,60+ RandomUtil.randomInt(1,5), TimeUnit.MINUTES);

        return Result.ok(DBShop);
    }

    /**
     * 更新商铺,
     * 数据更新策略:
     *  - 数据库如果更新,先对其数据库进行更新
     *  - 然后将缓存中的数据删掉
     *
     *  双写一致,但是没有用到 延迟双删
     *
     * @param shop
     * @return
     */
    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();

        if (id == null){
            return Result.fail("商铺id不能为空");
        }

        // 修改对应的商铺id
        int i = shopMapper.updateById(shop);

        // 删除缓存中商铺id
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
