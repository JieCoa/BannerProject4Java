package com.banner.crm.task;

import com.banner.common.message.BannerMessage;
import com.banner.crm.entity.BannerChangeOutbox;
import com.banner.crm.producer.BannerProducer;
import com.banner.crm.service.BannerOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 轮询 Outbox，将已提交的事件可靠投递到 Kafka。
 * 项目启动类中通过 @EnableScheduling 开启了定时任务功能，
 * fixedDelay 是“上一次方法执行结束后，间隔指定时间再执行下一次”。
 * 不过这里 Kafka 发送是异步的，dispatch() 注册完回调后就会结束，不会等待所有 Kafka 发送完成。
 * 因此这个延迟是从“提交完发送任务”开始计算的，不是从“所有消息发送成功”开始计算的。*/
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerOutboxTask {
    private final BannerOutboxService outboxService;
    private final BannerProducer producer;

    @Scheduled(fixedDelayString = "${banner.outbox-interval-millis:1000}")
    public void dispatch() {
        List<BannerChangeOutbox> events = outboxService.findPending(100);
        for (BannerChangeOutbox event : events) {
            try {
                BannerMessage message = outboxService.parse(event);

                // send() 返回：CompletableFuture<SendResult<String, String>>
                // whenComplete() 注册一个完成回调。无论发送成功还是失败，最终都会执行。
                producer.send(message).whenComplete((result, error) -> {
                    if (error == null) {
                        outboxService.markSent(event.getId());
                    } else {
                        log.error("Outbox Kafka 投递失败 eventId={} bannerId={}",
                                event.getEventId(), event.getBannerId(), error);
                        outboxService.markFailed(event.getId());
                    }
                });
            } catch (JsonProcessingException e) {
                log.error("Outbox payload 解析失败 eventId={}", event.getEventId(), e);
                outboxService.markFailed(event.getId());
            }
        }
    }
}
