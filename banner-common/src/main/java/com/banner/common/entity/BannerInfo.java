package com.banner.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * banner 信息，对应表 banner_info。
 * 商家在 [startTime, endTime] 时间段内投放活动宣传图，用户点击后跳转 jumpUrl。
 */
@Data
@TableName("banner_info")
public class BannerInfo {

    /** banner id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属业务 id（对应 business_info.id） */
    private Long bizId;

    /** 宣传标题 */
    private String title;

    /** 宣传图片地址 */
    private String imageUrl;

    /** 跳转 URL：直播间 / 活动页面 / 商品详情页 */
    private String jumpUrl;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 展示顺序，越小越靠前 */
    private Integer sort;

    /** 版本号（毫秒时间戳），随每次写库递增，用于消息乱序比较 */
    private Long version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
