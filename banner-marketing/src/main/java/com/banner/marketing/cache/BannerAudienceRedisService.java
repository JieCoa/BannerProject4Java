package com.banner.marketing.cache;

import com.banner.common.constant.BannerConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** 用户定向名单的 Redis 分桶缓存，避免一个 banner 产生超大的单个 value。 */
@Service
@RequiredArgsConstructor
public class BannerAudienceRedisService {

    private static final int BUCKET_SIZE = 1000;
    private static final String KEY_PREFIX = "banner:audience:";
    private final StringRedisTemplate redisTemplate;

    public void replace(Long bannerId, Set<Long> userIds, long version) {
        String indexKey = indexKey(bannerId);
        Set<String> oldKeys = redisTemplate.opsForSet().members(indexKey);
        if (oldKeys != null && !oldKeys.isEmpty()) {
            redisTemplate.delete(oldKeys);
        }
        redisTemplate.delete(indexKey);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new HashSet<>(userIds);
        int bucketNo = 0;
        Set<String> bucketKeys = new HashSet<>();
        Set<String> bucket = new HashSet<>();
        for (Long userId : distinct) {
            bucket.add(String.valueOf(userId));
            if (bucket.size() == BUCKET_SIZE) {
                bucketKeys.add(writeBucket(bannerId, bucketNo++, bucket, version));
                bucket.clear();
            }
        }
        if (!bucket.isEmpty()) {
            bucketKeys.add(writeBucket(bannerId, bucketNo, bucket, version));
        }
        redisTemplate.opsForSet().add(indexKey, bucketKeys.toArray(String[]::new));
        redisTemplate.expire(indexKey, Duration.ofDays(2));
    }

    public boolean hasAudience(Long bannerId) {
        Set<String> keys = redisTemplate.opsForSet().members(indexKey(bannerId));
        return keys != null && !keys.isEmpty();
    }

    public boolean contains(Long bannerId, Long userId) {
        Set<String> keys = redisTemplate.opsForSet().members(indexKey(bannerId));
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, String.valueOf(userId)))) {
                return true;
            }
        }
        return false;
    }

    private String writeBucket(Long bannerId, int bucketNo, Set<String> userIds, long version) {
        String key = KEY_PREFIX + bannerId + ":bucketIndx:" + bucketNo;
        redisTemplate.opsForSet().add(key, userIds.toArray(String[]::new));
        redisTemplate.expire(key, Duration.ofDays(2));
        return key;
    }

    private String indexKey(Long bannerId) {
        return KEY_PREFIX + bannerId + ":buckets";
    }
}
