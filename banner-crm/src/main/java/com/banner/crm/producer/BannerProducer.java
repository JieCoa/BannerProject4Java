package com.banner.crm.producer;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Banner Kafka Producer。发送结果由 Outbox 投递任务负责更新事件状态。 */
@Component
@RequiredArgsConstructor
public class BannerProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CompletableFuture<SendResult<String, String>> send(BannerMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            return kafkaTemplate.send(BannerConstants.BANNER_TOPIC, String.valueOf(message.getId()), json);
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
