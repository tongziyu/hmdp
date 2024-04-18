package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Ian
 * @since 2024年04月15日23:22:26
 */
@Service
    public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

        @Autowired
        private ShopTypeMapper shopTypeMapper;

        @Autowired
        private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商店类型[加缓存]
     * @return
     */
    @Override
    public Result queryTypeList() {

        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1.先去缓存里面查询
        String jsonShopTypeList = stringRedisTemplate.opsForValue().get(key);

        // 2.如果缓存里面有则直接返回
        if (StrUtil.isNotBlank(jsonShopTypeList)){

            // 将JSON数据准换成Java对象,封装到list里面,返回给前端
            List<ShopType> list = JSONUtil.toList(jsonShopTypeList, ShopType.class);
            return Result.ok(list);
        }


        // 3.从数据库里面查所有的商店类型
        List<ShopType> shopTypes = shopTypeMapper.selectList(null);

        // 4.将查询出来的数据保存进缓存里
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypes));

        // 设置缓存过期时间 + 设置过期时间+随机值,防止缓存雪崩
        stringRedisTemplate.expire(key,60+ RandomUtil.randomInt(1,5), TimeUnit.MINUTES);

        return Result.ok(shopTypes);
    }
}
