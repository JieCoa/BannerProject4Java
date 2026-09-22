package com.banner.crm.service;

import com.banner.common.entity.BannerInfo;
import com.banner.common.entity.BusinessInfo;
import com.banner.common.enums.OperateType;
import com.banner.common.message.BannerMessage;
import com.banner.crm.mapper.BannerMapper;
import com.banner.crm.mapper.BusinessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.banner.crm.mapper.BannerUserShardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Banner CRUD、数据库乐观锁和变更事件 Outbox。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper bannerMapper;
    private final BusinessMapper businessMapper;
    private final BannerUserShardMapper userShardMapper;
    private final BannerAudienceService audienceService;
    private final BannerOutboxService outboxService;

    @Transactional
    public BannerInfo create(BannerInfo banner) {
        validateRequired(banner);
        validateBusiness(banner.getBizId());
        validateTimeRange(banner);
        banner.setVersion(0L);
        banner.setDeleted(false);
        bannerMapper.insert(banner);
        // 将人群包数据存入数据库
        audienceService.replace(banner.getId(), banner.getUserIds(), banner.getVersion());
        banner.setBuckets(audienceService.bucketCount(banner.getUserIds()));
        saveEvent(BannerMessage.of(OperateType.CREATE, banner, resolveBizCode(banner.getBizId())));
        return banner;
    }

    /** 更新必须带 expected version；版本由数据库 version = version + 1 原子递增。 */
    @Transactional
    public BannerInfo update(BannerInfo patch) {
        if (patch.getId() == null || patch.getVersion() == null) {
            throw new IllegalArgumentException("更新必须携带 id 和 version");
        }
        BannerInfo current = requireBanner(patch.getId());
        validateBusiness(patch.getBizId() != null ? patch.getBizId() : current.getBizId());
        validateTimeRange(mergedForCheck(patch, current));
        if (bannerMapper.updateByIdAndVersion(patch, patch.getVersion()) != 1) {
            throw new OptimisticLockException("Banner 已被其他请求修改，请重新查询后再更新");
        }
        // 更新后的 banner 数据
        BannerInfo fresh = requireBanner(patch.getId());
        fresh.setDeleted(false);
        fresh.setBuckets(patch.getUserIds() != null
                ? audienceService.bucketCount(patch.getUserIds())
                : audienceService.bucketCount(fresh.getId()));

        // 更新人群包数据
        if (patch.getUserIds() != null) {
            audienceService.replace(fresh.getId(), patch.getUserIds(), fresh.getVersion());
        }
        saveEvent(BannerMessage.of(OperateType.UPDATE, fresh, resolveBizCode(fresh.getBizId())));
        return fresh;
    }

    /** 先递增版本并写 DELETE Outbox，再物理删除 Banner 与用户分片 */
    @Transactional
    public void delete(Long id, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new IllegalArgumentException("删除必须携带 version");
        }
        BannerInfo current = requireBanner(id);
        if (bannerMapper.incrementVersionBeforeDelete(id, expectedVersion) != 1) {
            throw new OptimisticLockException("Banner 已被其他请求修改，请重新查询后再删除");
        }
        long deleteVersion = expectedVersion + 1;
        current.setBuckets(audienceService.bucketCount(id));
        BannerMessage deleted = BannerMessage.deleted(current, resolveBizCode(current.getBizId()), deleteVersion);
        saveEvent(deleted);
        userShardMapper.delete(new LambdaQueryWrapper<com.banner.common.entity.BannerUserShard>()
                .eq(com.banner.common.entity.BannerUserShard::getBannerId, id));
        bannerMapper.deleteById(id);
    }

    public List<BannerInfo> list(Long bizId) {
        return bannerMapper.selectList(new LambdaQueryWrapper<BannerInfo>()
                .eq(bizId != null, BannerInfo::getBizId, bizId)
                .orderByAsc(BannerInfo::getSort)
                .orderByDesc(BannerInfo::getVersion));
    }

    public List<BannerInfo> listNotExpired() {
        return bannerMapper.selectList(new LambdaQueryWrapper<BannerInfo>()
                .ge(BannerInfo::getEndTime, LocalDateTime.now()));
    }

    public String resolveBizCode(Long bizId) {
        BusinessInfo business = businessMapper.selectById(bizId);
        if (business == null) {
            throw new IllegalArgumentException("业务不存在, bizId=" + bizId);
        }
        return business.getBizCode();
    }

    private void saveEvent(BannerMessage message) {
        outboxService.save(message);
    }

    private BannerInfo requireBanner(Long id) {
        BannerInfo banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new IllegalArgumentException("banner 不存在, id=" + id);
        }
        return banner;
    }

    private void validateRequired(BannerInfo banner) {
        if (isBlank(banner.getTitle()) || isBlank(banner.getImageUrl()) || isBlank(banner.getJumpUrl())) {
            throw new IllegalArgumentException("title / imageUrl / jumpUrl 不能为空");
        }
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

    private BannerInfo mergedForCheck(BannerInfo patch, BannerInfo current) {
        BannerInfo merged = new BannerInfo();
        merged.setStartTime(patch.getStartTime() != null ? patch.getStartTime() : current.getStartTime());
        merged.setEndTime(patch.getEndTime() != null ? patch.getEndTime() : current.getEndTime());
        return merged;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
