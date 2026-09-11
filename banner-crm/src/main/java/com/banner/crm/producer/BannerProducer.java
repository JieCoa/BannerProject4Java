package com.banner.crm.producer;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * banner 变更消息生产者。
 * 以 bannerId 作为消息 key：同一 banner 的消息进入同一分区，保证分区内有序，
 * 配合消费端 version 比对解决 insert/update 乱序问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void send(BannerMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(BannerConstants.BANNER_TOPIC, String.valueOf(message.getId()), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // 发送失败不阻断主流程：定时补偿任务会全量重发，最终一致
                            log.error("banner 消息发送失败 bannerId={}", message.getId(), ex);
                        } else {
                            log.info("banner 消息已发送 operateType={} bannerId={} version={} partition={} offset={}",
                                    message.getOperateType(), message.getId(), message.getVersion(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("banner 消息序列化失败 bannerId={}", message.getId(), e);
        }
    }
}
