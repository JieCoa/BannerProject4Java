package com.banner.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务线（按商品类别划分：助农产品 / 手机数码 / 服装 / 化妆品等），对应表 business_info
 */
@Data
@TableName("business_info")
public class BusinessInfo {

    /** 业务 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（英文，用于拼 Redis 缓存 key），如 agri / digital / clothes / beauty */
    private String bizCode;

    /** 业务名称，如 助农产品 */
    private String bizName;

    /** 业务描述 */
    private String description;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
