package com.banner.common.message;

import com.banner.common.entity.BannerInfo;
import com.banner.common.enums.OperateType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRM -> 营销系统的 Kafka 消息体。用户定向名单不进入消息，营销系统按 bannerId 回源 CRM。
 */
@Data
public class BannerMessage {

    private String messageId;
    private OperateType operateType;
    private Long id;
    private Long bizId;
    private String bizCode;
    private String title;
    private String imageUrl;
    private String jumpUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sort;
    private Long version;

    public static BannerMessage of(OperateType operateType, BannerInfo banner, String bizCode) {
        BannerMessage message = new BannerMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setOperateType(operateType);
        message.setId(banner.getId());
        message.setBizId(banner.getBizId());
        message.setBizCode(bizCode);
        message.setTitle(banner.getTitle());
        message.setImageUrl(banner.getImageUrl());
        message.setJumpUrl(banner.getJumpUrl());
        message.setStartTime(banner.getStartTime());
        message.setEndTime(banner.getEndTime());
        message.setSort(banner.getSort());
        message.setVersion(banner.getVersion());
        return message;
    }
}
