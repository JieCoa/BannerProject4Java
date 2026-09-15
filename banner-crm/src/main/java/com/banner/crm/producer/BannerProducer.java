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
 * 
 * @Component 注解：将 BannerProducer 类注册为 Spring 组件，使其可以被 Spring 容器管理。
 * 因此其他 Spring Bean，例如 BannerService 中，可以自动注入它：
 *   private final BannerProducer bannerProducer;
 * 
 * @RequiredArgsConstructor 注解：自动生成构造函数，注入 KafkaTemplate 和 ObjectMapper 依赖。
 * @Slf4j 注解：自动生成日志记录器。故，代码中可以直接使用 log 变量来记录日志。
 * 
 * whenComplete() 是一个回调函数，当 Kafka 发送操作最终完成时，会执行这段代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerProducer {

    // Spring Kafka 提供的 Kafka 操作工具类: KafkaTemplate<消息 key 类型, 消息 value 类型>
    private final KafkaTemplate<String, String> kafkaTemplate;
    // Jackson 提供的对象映射器：将 Java 对象转换为 JSON 字符串，或将 JSON 字符串转换为 Java 对象。
    private final ObjectMapper objectMapper;

    public void send(BannerMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            // send 方法参数：
            // 1. topic 名称
            // 2. message key: message.getId() 获取 bannerId
            // 3. message value
            // send 方法通常不会一直等待 Kafka 完成网络操作，而是立即返回一个结果对象：
            // CompletableFuture<SendResult<String, String>>，这里使用 void 表示不关心结果。
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
