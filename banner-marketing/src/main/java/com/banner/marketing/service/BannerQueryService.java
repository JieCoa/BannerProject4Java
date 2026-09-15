package com.banner.marketing.service;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.banner.marketing.cache.BannerRedisService;
import com.banner.marketing.localcache.LocalCache;
import com.banner.marketing.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 用户侧 banner 查询服务。
 * <p>
 * 三级查询链路：localCache -> Redis -> （miss 时返回空列表，由 CRM 补偿任务保证最终有数据）。
 * "用户在某一天能看到的 banner 集合" = 该业务线当天 key 的 Hash 全量，再按当前时刻过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerQueryService {

    private final BannerRedisService bannerRedisService;
    private final LocalCache localCache;

    /** 查询某业务线 "此刻" 可见的 banner 列表（已按 sort 升序） */
    public List<BannerVO> listVisibleBanners(String bizCode) {
        LocalDate today = LocalDate.now(BannerConstants.SHANGHAI);
        String localKey = BannerRedisService.localKey(bizCode, today);

        // 1. 本地缓存命中直接返回（微秒级，扛住高并发）
        List<BannerVO> cached = localCache.get(localKey);
        if (cached != null) {
            log.debug("localCache 命中 key={}", localKey);
            return cached;
        }

        // 2. 回源 Redis
        List<BannerVO> result = loadFromRedis(bizCode, today);

        // 3. 最新查询结果放入 localCache
        localCache.put(localKey, result);
        return result;
    }

    private List<BannerVO> loadFromRedis(String bizCode, LocalDate today) {
        LocalDateTime now = LocalDateTime.now(BannerConstants.SHANGHAI);
        try {
            return bannerRedisService.loadDay(bizCode, today).stream()
                    // 只保留"此刻"在生效期内的 banner
                    .filter(msg -> !now.isBefore(msg.getStartTime()) && !now.isAfter(msg.getEndTime()))
                    // nullsLast() 表示如果 sort 是 null，把它放到最后；naturalOrder() 表示使用自然顺序
                    .sorted(Comparator.comparing(BannerMessage::getSort,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(msg -> new BannerVO(msg.getId(), msg.getTitle(), msg.getImageUrl(),
                            msg.getJumpUrl(), msg.getSort()))
                    .toList();
        } catch (Exception e) {
            log.error("查询 Redis 缓存失败 bizCode={}", bizCode, e);
            // 创建的是一个不可修改的空列表
            return List.of();
        }
    }
}
