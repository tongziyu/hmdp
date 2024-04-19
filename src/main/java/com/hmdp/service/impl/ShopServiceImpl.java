package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
     * 创建线程池,实现逻辑过期中的线程调度
     */
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

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
     * 实现互斥锁,防止内存击穿
     * @return
     */
    @Override
    public Result queryShopWithMutex(Long id){
        /*
         * 防止内存击穿,使用互斥锁
         */
        // 1.现从redis中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String jsonShopObject = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否为空
        if (StrUtil.isNotBlank(jsonShopObject)){
            // 3.将json数据转换成Java对象
            Shop shop = JSONUtil.toBean(jsonShopObject, Shop.class);

            return Result.ok(shop);
        }
        String lock = "lock:shop:" + id;
        try {
            // 4. 如果数据库中没有则去拿互斥锁
            // 4.1 如果拿不到互斥锁,就继续拿

            Boolean isBoolean = tryLock(lock);

            if (!BooleanUtil.isTrue(isBoolean)){
                // 拿不到互斥锁,休眠一下,继续拿
                Thread.sleep(50);
                return queryShopWithMutex(id);
            }

            // 4.2 拿到互斥锁了,进数据库查询数据后重设缓存
            Shop shop = shopMapper.selectById(id);

            // 4.3 查询的数据为null,则表示没有这个商铺
            if (shop == null){
                return Result.fail("商铺不存在");
            }

            // 4.4 重设缓存
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

            return Result.ok(shop);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {

            // 4.5 释放锁
            unLock(lock);
        }

        //return Result.ok();
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


    /**
     * 获取互斥锁
     * @param lock
     * @return
     */
    private Boolean tryLock(String lock){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(lock, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    /**
     * 释放互斥锁
     * @param lock
     */
    private void unLock(String lock){
        stringRedisTemplate.delete(lock);
    }

    /**
     * 数据预热,将传入的id值对应的数据存入redis中
     * 使用逻辑过期防止缓存击穿
     */
    public RedisData saveShopRedis(Long id, Long expireSeconds){
        // 创建RedisData对象,里面有data数据和expire过期时间[逻辑过期时间]
        RedisData redisData = new RedisData();
        // 向数据库中查询shop信息
        Shop shop = shopMapper.selectById(id);
        // 封装shop信息
        redisData.setData(shop);

        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 向Redis中存储数据
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
        return redisData;
    }

    /**
     * 防止缓存击穿使用逻辑过期
     * @param id
     * @return
     */
    @Override
    public Result queryShopLogicalExpire(Long id){
        // 1.先去缓存中查询数据
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String redisJsonObject = stringRedisTemplate.opsForValue().get(key);

        // 判断如果缓存中查询出来为空,则返回店铺不存在
        if (StrUtil.isBlank(redisJsonObject)) {

            return Result.fail("店铺不存在!");
        }

        // 查询出来的json不为空,转换成对象
        RedisData redisData = JSONUtil.toBean(redisJsonObject, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        LocalDateTime now = LocalDateTime.now();

        // 判断是否过期
        // 如果没有过期,则返回数据
        if (expireTime.isAfter(now)){
            return Result.ok(redisData.getData());
        }
        // 过期了,开启线程 去重构数据
        EXECUTOR_SERVICE.submit(()->{
            // 获取互斥锁
            Boolean aBoolean = tryLock(RedisConstants.LOCK_SHOP_KEY + id);

            // 如果没有获取锁,则直接返回旧数据
            if (!aBoolean){
                return Result.ok(redisData.getData());
            }
            // 获得了锁,开始重构缓存
            try {
                RedisData redisData1 = saveShopRedis(id, 20L);
                return Result.ok(redisData1.getData());
            } finally {
                // 释放锁
                unLock(RedisConstants.LOCK_SHOP_KEY + id);
            }
        });


        return Result.ok(redisData.getData());
    }
}
