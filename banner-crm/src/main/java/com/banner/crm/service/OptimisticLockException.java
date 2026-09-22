package com.banner.crm.service;

/** 并发更新/删除时，客户端携带的 version 已失效。 */
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) {
        super(message);
    }
}
