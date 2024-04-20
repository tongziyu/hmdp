package com.hmdp.utils;

/**
 * 分布式锁
 */
public interface ILock {

    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(Integer timeoutSec);

    /**
     * 释放锁
     */
    void unLock();

}
