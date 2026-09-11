package com.banner.crm.service;

import com.banner.common.entity.BusinessInfo;
import com.banner.crm.mapper.BusinessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务线增删改查（banner 的挂靠主体）
 */
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessMapper businessMapper;

    public BusinessInfo create(BusinessInfo business) {
        LocalDateTime now = LocalDateTime.now();
        business.setCreateTime(now);
        business.setUpdateTime(now);
        business.setStatus(1);
        businessMapper.insert(business);
        return business;
    }

    public List<BusinessInfo> list() {
        return businessMapper.selectList(
                new LambdaQueryWrapper<BusinessInfo>().orderByAsc(BusinessInfo::getId));
    }
}
