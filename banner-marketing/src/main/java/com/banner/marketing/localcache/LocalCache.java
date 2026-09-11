package com.banner.marketing.localcache;

import com.banner.marketing.vo.BannerVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 本地缓存（进程内，Caffeine 实现）。
 * <p>
 * 查询链路：localCache 命中 -> 直接返回；未命中 -> 查 Redis -> 写入 localCache -> 返回。
 * TTL 较短（默认 30s），banner 变更时消费端会主动 invalidate，兼顾性能与实时性。
 */
@Component
public class LocalCache {

    private final Cache<String, List<BannerVO>> cache;

    public LocalCache(@Value("${banner.local-cache-ttl-seconds:30}") long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    /** 命中返回数据，未命中返回 null */
    public List<BannerVO> get(String key) {
        return cache.getIfPresent(key);
    }

    /** 写入缓存 */
    public void put(String key, List<BannerVO> value) {
        cache.put(key, value);
    }

    /** 失效（banner 消息消费成功后调用，让下一次查询回源 Redis 拿最新数据） */
    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
