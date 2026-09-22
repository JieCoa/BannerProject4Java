package com.banner.marketing.localcache;

import com.banner.common.message.BannerMessage;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 本地缓存第二类 Redis Key 的业务日期 Banner Map。 */
@Component
public class LocalCache {
    private final Cache<String, Map<Long, BannerMessage>> cache;

    public LocalCache(@Value("${banner.local-cache-ttl-seconds:30}") long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    public Map<Long, BannerMessage> get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, Map<Long, BannerMessage> value) {
        cache.put(key, value);
    }
}
