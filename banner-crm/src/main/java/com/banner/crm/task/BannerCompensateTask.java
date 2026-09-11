package com.banner.crm.task;

import com.banner.common.entity.BannerInfo;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.crm.producer.BannerProducer;
import com.banner.crm.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时补偿任务：解决"消息丢失"问题。
 * <p>
 * Kafka 消息可能因网络抖动 / broker 宕机等原因丢失，导致营销系统 Redis 缓存与 MySQL 不一致。
 * 本任务定时把所有"未过期"的 banner 全量重发到 Kafka（消息量小，可接受）。
 * 消费端按 version 幂等覆盖（version 相同或更小则忽略），因此重发是安全的，
 * 同时也能自愈"update 先于 insert 被消费"等乱序场景。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerCompensateTask {

    private final BannerService bannerService;
    private final BannerProducer bannerProducer;

    /** 每 5 分钟执行一次（可在 application.yml 的 banner.compensate-interval-minutes 调整） */
    @Scheduled(fixedRateString = "#{${banner.compensate-interval-minutes:5} * 60 * 1000}")
    public void compensate() {
        List<BannerInfo> banners = bannerService.listNotExpired();
        for (BannerInfo banner : banners) {
            String bizCode = bannerService.resolveBizCode(banner.getBizId());
            bannerProducer.send(BannerMessage.of(OperateType.CREATE, banner, bizCode));
        }
        log.info("补偿任务完成, 重发未过期 banner 数量={}", banners.size());
    }
}
