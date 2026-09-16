package com.banner.crm.controller;

import com.banner.crm.service.BannerAudienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 营销系统按 bannerId 回源获取定向用户的内部接口。 */
@RestController
@RequestMapping("/internal/banner-audience")
@RequiredArgsConstructor
public class BannerAudienceController {

    private final BannerAudienceService audienceService;

    @GetMapping("/{bannerId}")
    public Set<Long> getUserIds(@PathVariable Long bannerId) {
        return audienceService.findUserIds(bannerId);
    }
}
