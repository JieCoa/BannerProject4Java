package com.banner.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * banner 信息，对应表 banner_info。
 */
@Data
@TableName("banner_info")
public class BannerInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bizId;
    private String title;
    private String imageUrl;
    private String jumpUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer sort;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 不落 banner_info 表；CRM 请求使用，实际数据保存在 banner_user_shard */
    @TableField(exist = false)
    private Set<Long> userIds;
}
