package com.banner.marketing.consumer;

import com.banner.common.constant.BannerConstants;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.marketing.cache.BannerAudienceRedisService;
import com.banner.marketing.cache.BannerRedisService;
import com.banner.marketing.client.BannerAudienceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Kafka -> Redis；按三类 Key 和 buckets 差异执行有序更新。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerChangeConsumer {

    private final BannerRedisService bannerRedisService;
    private final BannerAudienceRedisService audienceRedisService;
    private final BannerAudienceClient audienceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = BannerConstants.BANNER_TOPIC, groupId = "banner-marketing-group")
    public void onMessage(String json) {
        try {
            BannerMessage message = objectMapper.readValue(json, BannerMessage.class);
            if (message.getId() == null || message.getOperateType() == null
                    || message.getVersion() == null) {
                log.warn("非法消息，丢弃: {}", json);
                return;
            }
            if (!bannerRedisService.shouldConsume(message)) {
                log.info("丢弃过期/乱序消息 bannerId={} version={}", message.getId(), message.getVersion());
                return;
            }

            if (message.getOperateType() == OperateType.DELETE
                    || Boolean.TRUE.equals(message.getDeleted())) {
                processDelete(message);
            } else {
                processCreateOrUpdate(message);
            }
        } catch (Exception e) {
            log.error("消息处理失败，跳过: {}", json, e);
        }
    }

    private void processDelete(BannerMessage message) throws Exception {
        BannerMessage old = bannerRedisService.loadBanner(message.getId());
        // 删除只维护第一、第二 Key；不回源 CRM，也不重建第三 Key。
        if (old != null) {
            bannerRedisService.removeFromBusinessMap(old);
        }
        bannerRedisService.removeFromBusinessMap(message);
        message.setDeleted(true);
        bannerRedisService.writeBanner(message);
        log.info("删除消息已处理 bannerId={} version={} oldBuckets={}",
                message.getId(), message.getVersion(), message.getBuckets());
    }

    private void processCreateOrUpdate(BannerMessage message) throws Exception {
        BannerMessage old = bannerRedisService.loadBanner(message.getId());
        int oldBuckets = old == null || old.getBuckets() == null ? 0 : old.getBuckets();
        Set<Long> userIds = audienceClient.fetchUserIds(message.getId());
        int newBuckets = resolveBuckets(message, userIds);
        message.setBuckets(newBuckets);

        if (newBuckets > oldBuckets) {
            // case1：先新增/覆盖第三 Key，再写第一 Key，最后写第二 Key。
            audienceRedisService.writeBuckets(message.getId(), userIds, newBuckets, message.getEndTime());
            writeFirstAndSecond(message, old);
        } else if (newBuckets == oldBuckets) {
            // case2：按流程图顺序覆盖第三、第一、第二 Key。
            audienceRedisService.writeBuckets(message.getId(), userIds, newBuckets, message.getEndTime());
            writeFirstAndSecond(message, old);
        } else {
            // case3：先收窄第一 Key，再写第二 Key，最后只写有效范围内的第三 Key。
            bannerRedisService.writeBanner(message);
            writeBusinessMapAfterRemovingOld(message, old);
            audienceRedisService.writeBuckets(message.getId(), userIds, newBuckets, message.getEndTime());
        }
        log.info("Banner 更新完成 bannerId={} oldBuckets={} newBuckets={}",
                message.getId(), oldBuckets, newBuckets);
    }

    private void writeFirstAndSecond(BannerMessage message, BannerMessage old) throws Exception {
        bannerRedisService.writeBanner(message);
        writeBusinessMapAfterRemovingOld(message, old);
    }

    private void writeBusinessMapAfterRemovingOld(BannerMessage message, BannerMessage old) throws Exception {
        if (old != null) {
            bannerRedisService.removeFromBusinessMap(old);
        }
        bannerRedisService.writeToBusinessMap(message);
    }

    private int resolveBuckets(BannerMessage message, Set<Long> userIds) {
        if (message.getBuckets() != null) {
            return message.getBuckets();
        }
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return (userIds.size() + BannerAudienceRedisService.BUCKET_SIZE - 1)
                / BannerAudienceRedisService.BUCKET_SIZE;
    }
}
