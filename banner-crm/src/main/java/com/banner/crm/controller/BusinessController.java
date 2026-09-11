package com.banner.crm.controller;

import com.banner.common.entity.BusinessInfo;
import com.banner.crm.service.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务线管理接口（运维人员使用）
 */
@RestController
@RequestMapping("/crm/business")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @PostMapping
    public BusinessInfo create(@RequestBody BusinessInfo business) {
        return businessService.create(business);
    }

    @GetMapping("/list")
    public List<BusinessInfo> list() {
        return businessService.list();
    }
}
