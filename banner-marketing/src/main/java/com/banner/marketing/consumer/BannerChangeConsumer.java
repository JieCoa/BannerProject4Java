package com.banner.marketing.consumer;

import com.banner.common.constant.BannerConstants;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.marketing.cache.BannerRedisService;
import com.banner.marketing.localcache.LocalCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * banner 变更消息消费者：Kafka -> Redis。
 * <p>
 * 乱序/幂等防护策略（对应"insert 后马上 update，但 update 可能先被消费"的问题）：
 * 1. CRM 端以 bannerId 为消息 key => 同一 banner 的消息在同一分区内有序；
 * 2. 消息携带 version，消费端只接受 version 更大的消息，迟到的旧消息直接丢弃；
 * 3. DELETE 写墓碑（TTL 24h），防止乱序的旧消息复活已删 banner；
 * 4. CRM 端定时补偿任务全量重发，最终一致（自愈兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerChangeConsumer {

    private final BannerRedisService bannerRedisService;
    private final LocalCache localCache;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = BannerConstants.BANNER_TOPIC, groupId = "banner-marketing-group")
    public void onMessage(String json) {
        try {
            BannerMessage message = objectMapper.readValue(json, BannerMessage.class);
            if (message.getId() == null || message.getOperateType() == null) {
                log.warn("非法消息, 丢弃: {}", json);
                return;
            }

            // TODO 消息幂等：基于 messageId 去重（例如 Redis SETNX message:{messageId}，成功才处理）。
            //      当前已通过 version 比对 + 墓碑保证"重复消费结果不变"，暂不实现显式去重表。

            if (!bannerRedisService.shouldConsume(message)) {
                log.info("丢弃过期/乱序消息 operateType={} bannerId={} msgVersion={} (已存版本更新或有墓碑)",
                        message.getOperateType(), message.getId(), message.getVersion());
                return;
            }

            // 根据 operateType 执行不同的操作
            if (message.getOperateType() == OperateType.DELETE) {
                bannerRedisService.applyDelete(message);
            } else {
                bannerRedisService.applyCreateOrUpdate(message);
            }
            // banner 数据变化后，把受影响的本地缓存条目失效，用户端立即看到最新数据
            // affectedLocalKeys() 方法返回一个 Set<String>，表示受影响的本地缓存条目的 key。
            bannerRedisService.affectedLocalKeys(message).forEach(localCache::invalidate);
            log.info("消息消费成功 operateType={} bannerId={} version={}",
                    message.getOperateType(), message.getId(), message.getVersion());
        } catch (Exception e) {
            // 解析失败等不可恢复异常：记录日志并跳过，避免阻塞消费队列；补偿任务会重发正确数据
            log.error("消息处理失败, 跳过: {}", json, e);
        }
    }
}
