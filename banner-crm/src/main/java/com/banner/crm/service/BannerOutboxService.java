package com.banner.crm.service;

import com.banner.common.message.BannerMessage;
import com.banner.crm.entity.BannerChangeOutbox;
import com.banner.crm.mapper.BannerChangeOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 变更事件 Outbox：和 MySQL 业务写入使用同一事务，确保物理删除后仍能补偿 DELETE。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerOutboxService {
    /** 事件已写入数据库，等待投递 */
    private static final String PENDING = "PENDING";
    /** 已成功发送到 Kafka */
    private static final String SENT = "SENT";
    /** 上次发送失败，等待重试 */
    private static final String FAILED = "FAILED";

    private final BannerChangeOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /** 将 Banner 变更消息持久化到 Outbox 表 */
    @Transactional
    public BannerChangeOutbox save(BannerMessage message) {
        // 检查事件是否已经存在：一个 Banner 的一个版本只能产生一条事件。
        BannerChangeOutbox existing = outboxMapper.selectOne(new LambdaQueryWrapper<BannerChangeOutbox>()
                .eq(BannerChangeOutbox::getBannerId, message.getId())
                .eq(BannerChangeOutbox::getVersion, message.getVersion()));
        if (existing != null) {
            return existing;
        }
        try {
            BannerChangeOutbox event = new BannerChangeOutbox();
            // messageId 本身就是通过 UUID 生成的
            event.setEventId(message.getMessageId() == null ? UUID.randomUUID().toString() : message.getMessageId());
            event.setBannerId(message.getId());
            event.setOperateType(message.getOperateType().name());
            event.setVersion(message.getVersion());
            // 将完整的 BannerMessage 转换成 JSON，保存到 payload 列
            event.setPayload(objectMapper.writeValueAsString(message));

            // 初始化投递状态
            event.setStatus(PENDING);
            event.setRetryCount(0);
            event.setNextRetryTime(LocalDateTime.now());
            outboxMapper.insert(event);
            return event;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Banner 消息序列化失败", e);
        }
    }

    /** 查询待发送事件 */
    public List<BannerChangeOutbox> findPending(int limit) {
        return outboxMapper.selectList(new LambdaQueryWrapper<BannerChangeOutbox>()
                .in(BannerChangeOutbox::getStatus, PENDING, FAILED)
                .le(BannerChangeOutbox::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(BannerChangeOutbox::getId)
                .last("LIMIT " + limit));
    }

    /** 标记发送成功 */
    @Transactional
    public void markSent(Long id) {
        BannerChangeOutbox event = new BannerChangeOutbox();
        event.setId(id);
        event.setStatus(SENT);
        event.setUpdateTime(LocalDateTime.now());
        outboxMapper.updateById(event);
    }

    /** 记录失败并安排重试 */
    @Transactional
    public void markFailed(Long id) {
        // 需要读取原来的 retryCount，所以这里不能像 markSent() 那样只创建一个部分对象
        BannerChangeOutbox event = outboxMapper.selectById(id);
        if (event == null) {
            return;
        }
        event.setStatus(FAILED);
        event.setRetryCount(event.getRetryCount() + 1);
        // 计算下一次重试时间：相当于计算 2ⁿ，形成指数退避。这样 Kafka 故障时不会每秒不断重试，减轻 Kafka 和数据库压力。
        event.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(event.getRetryCount(), 8))));
        event.setUpdateTime(LocalDateTime.now());
        outboxMapper.updateById(event);
    }

    /** 还原 Kafka 消息 */
    public BannerMessage parse(BannerChangeOutbox event) throws JsonProcessingException {
        return objectMapper.readValue(event.getPayload(), BannerMessage.class);
    }
}
