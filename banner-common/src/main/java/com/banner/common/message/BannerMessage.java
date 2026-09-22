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

    /** 数据库乐观锁版本，消费端只接受严格更大的版本 */
    private Long version;

    /** Redis 用户名单桶数量，消息不携带 userId，仅携带桶数量 */
    private Integer buckets;

    /** Redis 删除状态 */
    private Boolean deleted;

    public static BannerMessage of(OperateType operateType, BannerInfo banner, String bizCode) {
        BannerMessage message = baseMessage(operateType, banner, bizCode);
        message.setDeleted(false);
        return message;
    }

    /** 构造物理删除事件：版本必须是删除前版本递增后的新版本 */
    public static BannerMessage deleted(BannerInfo banner, String bizCode, long deleteVersion) {
        BannerMessage message = baseMessage(OperateType.DELETE, banner, bizCode);
        message.setVersion(deleteVersion);
        message.setDeleted(true);
        return message;
    }

    private static BannerMessage baseMessage(OperateType operateType, BannerInfo banner, String bizCode) {
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
        message.setBuckets(banner.getBuckets());
        return message;
    }
}
