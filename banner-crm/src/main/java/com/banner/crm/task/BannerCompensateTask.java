package com.banner.crm.task;

import com.banner.common.entity.BannerInfo;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.crm.service.BannerOutboxService;
import com.banner.crm.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Banner 业务补偿：只重发仍存在且未过期的 Banner。
 * DELETE 事件由 BannerOutboxTask 负责补偿，不能从已物理删除的主表重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannerCompensateTask {

    private final BannerService bannerService;
    private final BannerOutboxService outboxService;

    @Scheduled(fixedRateString = "#{${banner.compensate-interval-minutes:5} * 60 * 1000}")
    public void compensate() {
        List<BannerInfo> banners = bannerService.listNotExpired();
        for (BannerInfo banner : banners) {
            String bizCode = bannerService.resolveBizCode(banner.getBizId());
            outboxService.save(BannerMessage.of(OperateType.CREATE, banner, bizCode));
        }
        log.info("Banner 补偿事件已写入 Outbox, 数量={}", banners.size());
    }
}
