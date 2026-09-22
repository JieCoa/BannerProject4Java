package com.banner.marketing.cache;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Redis 第一、第二类 Key：单 Banner 状态和业务日期 Banner Map。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BannerMessage loadBanner(Long bannerId) throws Exception {
        String json = redisTemplate.opsForValue().get(BannerConstants.bannerKey(bannerId));
        return json == null ? null : objectMapper.readValue(json, BannerMessage.class);
    }

    public long storedVersion(Long bannerId) {
        try {
            BannerMessage message = loadBanner(bannerId);
            return message == null || message.getVersion() == null
                    ? Long.MIN_VALUE : message.getVersion();
        } catch (Exception e) {
            log.error("读取 Banner 状态失败 bannerId={}", bannerId, e);
            return Long.MIN_VALUE;
        }
    }

    public boolean shouldConsume(BannerMessage message) {
        return message.getVersion() != null
                && message.getVersion() > storedVersion(message.getId());
    }

    public void writeBanner(BannerMessage message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        String key = BannerConstants.bannerKey(message.getId());
        redisTemplate.opsForValue().set(key, json);
        redisTemplate.expireAt(key, expireAt(message.getEndTime()));
    }

    public void removeFromBusinessMap(BannerMessage message) {
        if (message == null || message.getBizCode() == null
                || message.getStartTime() == null || message.getEndTime() == null) {
            return;
        }
        for (LocalDate day : BannerConstants.coveredDays(message.getStartTime(), message.getEndTime())) {
            redisTemplate.opsForHash().delete(
                    BannerConstants.bannerBusinessDayKey(message.getBizCode(), day),
                    String.valueOf(message.getId()));
        }
    }

    public void writeToBusinessMap(BannerMessage message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        for (LocalDate day : BannerConstants.coveredDays(message.getStartTime(), message.getEndTime())) {
            String key = BannerConstants.bannerBusinessDayKey(message.getBizCode(), day);
            redisTemplate.opsForHash().put(key, String.valueOf(message.getId()), json);
            redisTemplate.expireAt(key, expireAt(day.atStartOfDay()));
        }
    }

    public List<BannerMessage> loadBusinessDay(String bizCode, LocalDate day) throws Exception {
        var entries = redisTemplate.<String, String>opsForHash()
                .entries(BannerConstants.bannerBusinessDayKey(bizCode, day));
        List<BannerMessage> result = new ArrayList<>();
        for (String json : entries.values()) {
            BannerMessage message = objectMapper.readValue(json, BannerMessage.class);
            if (!Boolean.TRUE.equals(message.getDeleted())) {
                result.add(message);
            }
        }
        return result;
    }

    private java.util.Date expireAt(LocalDateTime endTime) {
        return java.util.Date.from(endTime.toLocalDate().plusDays(1)
                .atStartOfDay(BannerConstants.SHANGHAI).toInstant());
    }
}
