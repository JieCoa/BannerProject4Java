package com.banner.marketing.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户侧 banner 视图对象（只暴露展示所需的最小字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BannerVO {

    /** banner id */
    private Long id;

    /** 宣传标题 */
    private String title;

    /** 宣传图片地址 */
    private String imageUrl;

    /** 跳转 URL：直播间 / 活动页面 / 商品详情页（用户点击 banner 后跳转） */
    private String jumpUrl;

    /** 展示顺序 */
    private Integer sort;
}
