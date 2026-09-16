package com.banner.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个 banner 的一段用户范围。每行最多保存 1000 个 userId，避免单行和单表记录过大。
 */
@Data
@TableName("banner_user_shard")
public class BannerUserShard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bannerId;

    /** 分片序号，从 0 开始 */
    private Integer shardNo;

    /** 逗号分隔的 userId，最多 1000 个 */
    private String userIds;

    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
