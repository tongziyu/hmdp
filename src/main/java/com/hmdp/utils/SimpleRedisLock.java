package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.Key;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Description:
 * @Author: Ian
 * @Date: 2024/4/20 11:04
 */
public class SimpleRedisLock implements ILock{

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 加载Lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean tryLock(Integer timeoutSec) {
        String key = KEY_PREFIX + name;

        long id = Thread.currentThread().getId();
        String value = ID_PREFIX+ id;

        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(aBoolean);
    }

    /**
     * 释放锁 使用Lua脚本,原子性问题得到解决
     */
    @Override
    public void unLock() {
        long id = Thread.currentThread().getId();
        String key = KEY_PREFIX + name;

        String value = ID_PREFIX+ id;

        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key),value);

    }


    /**
     * 释放锁

    @Override
    public void unLock() {
        // 释放锁之前,判断一下value是否是设置的value,是否已经被修改,如果修改了则不删除,如果没修改则删除
        long id = Thread.currentThread().getId();
        String key = KEY_PREFIX + name;

        String value = ID_PREFIX+ id;
        String redisValue = stringRedisTemplate.opsForValue().get(key);
        if (value.equals(redisValue)){
            stringRedisTemplate.delete(key);
        }
    }*/
}
