package com.banner.marketing.service;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.banner.marketing.cache.BannerAudienceRedisService;
import com.banner.marketing.cache.BannerRedisService;
import com.banner.marketing.localcache.LocalCache;
import com.banner.marketing.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户侧查询：localCache 缓存业务日期 Map，查询时再按时间和 userId 过滤。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerQueryService {

    private final BannerRedisService bannerRedisService;
    private final BannerAudienceRedisService audienceRedisService;
    private final LocalCache localCache;

    public List<BannerVO> listVisibleBanners(String bizCode, Long userId) {
        LocalDate today = LocalDate.now(BannerConstants.SHANGHAI);
        String localKey = BannerConstants.bannerBusinessDayKey(bizCode, today);
        Map<Long, BannerMessage> bannerMap = localCache.get(localKey);
        if (bannerMap == null) {
            bannerMap = loadBusinessMap(bizCode, today);
            localCache.put(localKey, bannerMap);
        }
        return filterVisible(bannerMap, userId);
    }

    private Map<Long, BannerMessage> loadBusinessMap(String bizCode, LocalDate day) {
        try {
            List<BannerMessage> messages = bannerRedisService.loadBusinessDay(bizCode, day);
            Map<Long, BannerMessage> result = new LinkedHashMap<>();
            for (BannerMessage message : messages) {
                result.put(message.getId(), message);
            }
            return result;
        } catch (Exception e) {
            log.error("查询 Redis Banner Map 失败 bizCode={} day={}", bizCode, day, e);
            return Map.of();
        }
    }

    private List<BannerVO> filterVisible(Map<Long, BannerMessage> bannerMap, Long userId) {
        LocalDateTime now = LocalDateTime.now(BannerConstants.SHANGHAI);
        return bannerMap.values().stream()
                .filter(message -> !now.isBefore(message.getStartTime())
                        && !now.isAfter(message.getEndTime()))
                .filter(message -> message.getBuckets() == null || message.getBuckets() == 0
                        || (userId != null && audienceRedisService.contains(
                                message.getId(), userId, message.getBuckets())))
                .sorted(Comparator.comparing(BannerMessage::getSort,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(message -> new BannerVO(message.getId(), message.getTitle(),
                        message.getImageUrl(), message.getJumpUrl(), message.getSort()))
                .toList();
    }
}
