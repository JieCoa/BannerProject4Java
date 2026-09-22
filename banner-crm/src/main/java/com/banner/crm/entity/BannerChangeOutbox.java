package com.banner.crm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 持久化的变更事件；物理删除 Banner 后仍可依靠该表补偿 DELETE。 */
@Data
@TableName("banner_change_outbox")
public class BannerChangeOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long bannerId;
    private String operateType;
    private Long version;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
