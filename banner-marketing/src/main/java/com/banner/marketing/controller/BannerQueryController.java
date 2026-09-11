package com.banner.marketing.controller;

import com.banner.marketing.service.BannerQueryService;
import com.banner.marketing.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户侧 banner 查询接口。
 * 前端拿到列表后渲染 banner 图，用户点击时用 jumpUrl 跳转到直播间/活动页/商品详情页。
 */
@RestController
@RequestMapping("/marketing")
@RequiredArgsConstructor
public class BannerQueryController {

    private final BannerQueryService bannerQueryService;

    /** 例：GET /marketing/banners?bizCode=agri */
    @GetMapping("/banners")
    public List<BannerVO> listVisibleBanners(@RequestParam String bizCode) {
        return bannerQueryService.listVisibleBanners(bizCode);
    }
}
