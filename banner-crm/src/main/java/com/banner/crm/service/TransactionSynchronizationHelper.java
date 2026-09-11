package com.banner.crm.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务同步工具：确保"事务提交成功后"再执行动作（如发 Kafka 消息）。
 * 若当前没有事务，则立即执行。
 */
public final class TransactionSynchronizationHelper {

    private TransactionSynchronizationHelper() {
    }

    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
