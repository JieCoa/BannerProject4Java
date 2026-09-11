package com.banner.crm.service;

import com.banner.common.entity.BannerInfo;
import com.banner.common.entity.BusinessInfo;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.crm.mapper.BannerMapper;
import com.banner.crm.mapper.BusinessMapper;
import com.banner.crm.producer.BannerProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * banner 增删改查 + 写库后发 Kafka 消息。
 * 注意：先事务提交写 MySQL，提交成功后再发消息（afterCommit），保证"库是事实源头"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper bannerMapper;
    private final BusinessMapper businessMapper;
    private final BannerProducer bannerProducer;

    /** 新增 banner */
    @Transactional
    public BannerInfo create(BannerInfo banner) {
        if (isBlank(banner.getTitle()) || isBlank(banner.getImageUrl()) || isBlank(banner.getJumpUrl())) {
            throw new IllegalArgumentException("title / imageUrl / jumpUrl 不能为空");
        }
        validateBusiness(banner.getBizId());
        validateTimeRange(banner);
        banner.setVersion(System.currentTimeMillis());
        bannerMapper.insert(banner);
        sendAfterCommit(OperateType.CREATE, banner);
        return banner;
    }

    /** 更新 banner（局部更新：只改传入的非空字段；消息始终用更新后的完整数据） */
    @Transactional
    public BannerInfo update(BannerInfo banner) {
        if (banner.getId() == null) {
            throw new IllegalArgumentException("更新必须携带 banner id");
        }
        BannerInfo db = bannerMapper.selectById(banner.getId());
        if (db == null) {
            throw new IllegalArgumentException("banner 不存在, id=" + banner.getId());
        }
        validateBusiness(banner.getBizId() != null ? banner.getBizId() : db.getBizId());
        validateTimeRange(mergedForCheck(banner, db));
        banner.setUpdateTime(LocalDateTime.now());
        banner.setVersion(System.currentTimeMillis());
        bannerMapper.updateById(banner);
        // 关键：updateById 是局部更新，必须回查完整行发消息，
        // 否则请求里为 null 的字段(如只改 title 时)会以 null 覆盖 Redis 中的 jumpUrl 等完整数据
        BannerInfo fresh = bannerMapper.selectById(banner.getId());
        sendAfterCommit(OperateType.UPDATE, fresh);
        return fresh;
    }

    /** 用请求中的非空字段覆盖 db 值后做时间校验（仅用于参数检查，不落库） */
    private BannerInfo mergedForCheck(BannerInfo patch, BannerInfo db) {
        BannerInfo merged = new BannerInfo();
        merged.setStartTime(patch.getStartTime() != null ? patch.getStartTime() : db.getStartTime());
        merged.setEndTime(patch.getEndTime() != null ? patch.getEndTime() : db.getEndTime());
        return merged;
    }

    /** 删除 banner */
    @Transactional
    public void delete(Long id) {
        BannerInfo db = bannerMapper.selectById(id);
        if (db == null) {
            return;
        }
        bannerMapper.deleteById(id);
        sendAfterCommit(OperateType.DELETE, db);
    }

    /** banner 列表（可按业务过滤） */
    public List<BannerInfo> list(Long bizId) {
        LambdaQueryWrapper<BannerInfo> wrapper = new LambdaQueryWrapper<BannerInfo>()
                .eq(bizId != null, BannerInfo::getBizId, bizId)
                .orderByAsc(BannerInfo::getSort)
                .orderByDesc(BannerInfo::getVersion);
        return bannerMapper.selectList(wrapper);
    }

    /** 未过期 banner（补偿任务用：end_time >= now 的都重发，覆盖"未来才生效"的场景） */
    public List<BannerInfo> listNotExpired() {
        return bannerMapper.selectList(new LambdaQueryWrapper<BannerInfo>()
                .ge(BannerInfo::getEndTime, LocalDateTime.now()));
    }

    private void sendAfterCommit(OperateType operateType, BannerInfo banner) {
        TransactionSynchronizationHelper.afterCommit(() -> {
            String bizCode = resolveBizCode(banner.getBizId());
            bannerProducer.send(BannerMessage.of(operateType, banner, bizCode));
        });
    }

    public String resolveBizCode(Long bizId) {
        BusinessInfo business = businessMapper.selectById(bizId);
        if (business == null) {
            throw new IllegalArgumentException("业务不存在, bizId=" + bizId);
        }
        return business.getBizCode();
    }

    private void validateBusiness(Long bizId) {
        if (bizId == null || businessMapper.selectById(bizId) == null) {
            throw new IllegalArgumentException("业务不存在, bizId=" + bizId);
        }
    }

    private void validateTimeRange(BannerInfo banner) {
        if (banner.getStartTime() == null || banner.getEndTime() == null
                || !banner.getEndTime().isAfter(banner.getStartTime())) {
            throw new IllegalArgumentException("时间非法: endTime 必须晚于 startTime");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
