package com.banner.common.message;

import com.banner.common.entity.BannerInfo;
import com.banner.common.enums.OperateType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CRM -> 营销系统 的 Kafka 消息体（JSON 序列化传输）。
 * 携带 banner 全量字段，消费端无需反查 MySQL 即可写入 Redis。
 */
@Data
public class BannerMessage {

    /** 消息唯一 id（用于消息幂等去重，TODO 见消费端） */
    private String messageId;

    /** 操作类型：CREATE / UPDATE / DELETE */
    private OperateType operateType;

    /** banner 全量字段 */
    private Long id;
    private Long bizId;
    /** 业务编码（冗余进消息，营销系统拼 Redis key 时无需查库） */
    private String bizCode;
    private String title;
    private String imageUrl;
    private String jumpUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sort;

    /** 版本号（= banner_info.version），乱序消息按此比较，旧消息直接丢弃 */
    private Long version;

    /** of 是一个静态工厂方法，用于创建 BannerMessage 对象 */
    public static BannerMessage of(OperateType operateType, BannerInfo banner, String bizCode) {
        BannerMessage msg = new BannerMessage();
        msg.setMessageId(java.util.UUID.randomUUID().toString());
        msg.setOperateType(operateType);
        msg.setId(banner.getId());
        msg.setBizId(banner.getBizId());
        msg.setBizCode(bizCode);
        msg.setTitle(banner.getTitle());
        msg.setImageUrl(banner.getImageUrl());
        msg.setJumpUrl(banner.getJumpUrl());
        msg.setStartTime(banner.getStartTime());
        msg.setEndTime(banner.getEndTime());
        msg.setSort(banner.getSort());
        msg.setVersion(banner.getVersion());
        return msg;
    }
}
