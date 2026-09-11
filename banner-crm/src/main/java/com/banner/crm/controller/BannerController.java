package com.banner.crm.controller;

import com.banner.common.entity.BannerInfo;
import com.banner.crm.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * banner 管理接口（运维人员使用）
 */
@RestController
@RequestMapping("/crm/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @PostMapping
    public BannerInfo create(@RequestBody BannerInfo banner) {
        return bannerService.create(banner);
    }

    @PutMapping
    public BannerInfo update(@RequestBody BannerInfo banner) {
        return bannerService.update(banner);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        bannerService.delete(id);
        return "ok";
    }

    @GetMapping("/list")
    public List<BannerInfo> list(@RequestParam(required = false) Long bizId) {
        return bannerService.list(bizId);
    }
}
