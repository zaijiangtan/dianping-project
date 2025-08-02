package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeout 锁持有的最长时间，超时自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    boolean tryLock(long timeout);

    /**
     * 释放锁
     */
    void unlock();
}
