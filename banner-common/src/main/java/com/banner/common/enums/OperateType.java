package com.banner.common.enums;

/**
 * banner 变更操作类型（Kafka 消息携带，供营销系统区分处理）
 */
public enum OperateType {

    /** 新增 / 重发（补偿任务全量重发也使用该类型，消费端按 version 幂等覆盖） */
    CREATE,

    /** 更新 */
    UPDATE,

    /** 删除（消费端写墓碑记录，防止迟到的旧消息复活已删 banner） */
    DELETE
}
