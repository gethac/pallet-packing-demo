package com.example.pallet.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 订单+批次级保存锁（简化版） */
@Component
public class PalletSaveLock {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String orderId, String batchId, Supplier<T> action) {
        String key = orderId + "#" + (batchId == null ? "" : batchId);
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        boolean ok;
        try {
            ok = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取托盘保存锁被中断");
        }
        if (!ok) {
            throw new IllegalStateException("托盘方案正在保存，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
