package com.banner.marketing.cache;

import com.banner.common.constant.BannerConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 用户人群包 Redis 分桶；不维护桶索引集合，桶范围由 Banner.buckets 控制。 */
@Service
@RequiredArgsConstructor
public class BannerAudienceRedisService {

    public static final int BUCKET_SIZE = 1000;
    private final StringRedisTemplate redisTemplate;

    public int bucketCount(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        long count = userIds.stream().filter(java.util.Objects::nonNull).distinct().count();
        return (int) ((count + BUCKET_SIZE - 1) / BUCKET_SIZE);
    }

    /** 按稳定顺序切桶，写入 [0, buckets) 范围。 */
    public void writeBuckets(Long bannerId, Set<Long> userIds, int buckets, LocalDateTime endTime) {
        if (userIds == null || userIds.isEmpty() || buckets <= 0) {
            return;
        }
        List<Long> ids = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (int bucketIndex = 0; bucketIndex < buckets; bucketIndex++) {
            int from = bucketIndex * BUCKET_SIZE;
            if (from >= ids.size()) {
                break;
            }
            int to = Math.min(from + BUCKET_SIZE, ids.size());
            Set<String> members = new HashSet<>();
            for (Long id : ids.subList(from, to)) {
                members.add(String.valueOf(id));
            }
            String key = BannerConstants.bannerBucketKey(bannerId, bucketIndex);
            redisTemplate.delete(key);
            redisTemplate.opsForSet().add(key, members.toArray(String[]::new));
            redisTemplate.expireAt(key, expireAt(endTime));
        }
    }

    /** 查询时只扫描有效桶，超出 buckets 的懒删除桶不可见。 */
    public boolean contains(Long bannerId, Long userId, int buckets) {
        if (userId == null || buckets <= 0) {
            return false;
        }
        String member = String.valueOf(userId);
        for (int bucketIndex = 0; bucketIndex < buckets; bucketIndex++) {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(
                    BannerConstants.bannerBucketKey(bannerId, bucketIndex), member))) {
                return true;
            }
        }
        return false;
    }

    /** 删除场景不主动清理桶，统一交给第三类 Key 的 TTL 自然过期。 */
    public void retainForLazyExpiry(Long bannerId, int buckets) {
        // 保留显式方法表达 Consumer 删除分支不回源、不重建、不删除桶的语义。
    }

    private java.util.Date expireAt(LocalDateTime endTime) {
        return java.util.Date.from(endTime.toLocalDate().plusDays(1)
                .atStartOfDay(com.banner.common.constant.BannerConstants.SHANGHAI).toInstant());
    }
}
