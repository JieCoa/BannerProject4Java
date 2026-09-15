package com.banner.marketing.cache;

import com.banner.common.constant.BannerConstants;
import com.banner.common.message.BannerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis 缓存读写（营销系统对 Redis 的唯一访问入口）。
 * <p>
 * 键值设计（对应业务含义"用户在某一天能看到的 banner 集合"）：
 * <ul>
 *   <li>key:   banner:{bizCode}:{yyyyMMdd}   —— 业务线 + banner 有效时间（按天切分）</li>
 *   <li>field: bannerId</li>
 *   <li>value: BannerMessage JSON（含生效起止时间、jumpUrl、version 等）</li>
 * </ul>
 * 另有两个辅助 key：
 * <ul>
 *   <li>banner:data:{bannerId}  —— 最新一条消息 JSON，用于乱序时取旧数据、回滚旧的覆盖天数</li>
 *   <li>banner:tomb:{bannerId}  —— 删除墓碑（存 version，TTL 24h），防止乱序消息复活已删 banner</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 新增/更新：写入覆盖到的每一天的 key，并记录最新数据 */
    public void applyCreateOrUpdate(BannerMessage message) throws Exception {
        // 1. 清理旧的覆盖天数（比如 update 把时间段从 3 天缩短为 1 天，多余天数要删掉）
        String oldJson = redisTemplate.opsForValue().get(BannerConstants.BANNER_DATA_PREFIX + message.getId());
        if (oldJson != null) {
            BannerMessage old = objectMapper.readValue(oldJson, BannerMessage.class);
            removeDayEntries(old);
        }
        // 2. 写入新的覆盖天数，put 方法：key, field 和 value 三个参数
        String json = objectMapper.writeValueAsString(message);
        for (var day : BannerConstants.coveredDays(message.getStartTime(), message.getEndTime())) {
            String key = BannerConstants.bannerDayKey(message.getBizCode(), day);
            redisTemplate.opsForHash().put(key, String.valueOf(message.getId()), json);
            // 设置过期时间
            redisTemplate.expireAt(key, expireAt(day));
        }
        // 3. 记录最新数据（无 TTL，作为乱序比较与旧数据回滚的依据）
        redisTemplate.opsForValue().set(BannerConstants.BANNER_DATA_PREFIX + message.getId(), json);
        log.info("Redis 写入 bannerId={} bizCode={} 覆盖天数={}",
                message.getId(), message.getBizCode(),
                BannerConstants.coveredDays(message.getStartTime(), message.getEndTime()).size());
    }

    /** 删除：写墓碑 + 清理所有覆盖天数 */
    public void applyDelete(BannerMessage message) throws Exception {
        String oldJson = redisTemplate.opsForValue().get(BannerConstants.BANNER_KEY_PREFIX + "data:" + message.getId());
        if (oldJson != null) {
            removeDayEntries(objectMapper.readValue(oldJson, BannerMessage.class));
        }
        removeDayEntries(message);
        // 墓碑：防止乱序/重复的旧消息把已删 banner 复活。墓碑不会永久存在，而是保留一段时间，例如 24 小时。
        //      这样可以防止短时间内的乱序消息复活数据，同时避免墓碑永久占用 Redis。
        redisTemplate.opsForValue().set(
                BannerConstants.BANNER_TOMB_PREFIX + message.getId(),
                String.valueOf(message.getVersion()),
                java.time.Duration.ofHours(BannerConstants.TOMBSTONE_TTL_HOURS));
        
        // 
        redisTemplate.delete(BannerConstants.BANNER_DATA_PREFIX + message.getId());
        log.info("Redis 删除 bannerId={} bizCode={}", message.getId(), message.getBizCode());
    }

    /** 读取某业务线某天缓存的所有 banner 消息（Redis 的 HGETALL 操作） */
    public List<BannerMessage> loadDay(String bizCode, java.time.LocalDate day) throws Exception {
        var entries = redisTemplate.<String, String>opsForHash()
                .entries(BannerConstants.bannerDayKey(bizCode, day));
        List<BannerMessage> result = new java.util.ArrayList<>();
        for (String json : entries.values()) {
            result.add(objectMapper.readValue(json, BannerMessage.class));
        }
        return result;
    }

    /** 乱序防护用的版本比较：返回已存储的版本号（无数据时返回 Long.MIN_VALUE；有墓碑时返回墓碑版本） */
    public long storedVersion(Long bannerId) {
        // 如果墓碑存在，就返回墓碑版本
        String tomb = redisTemplate.opsForValue().get(BannerConstants.BANNER_TOMB_PREFIX + bannerId);
        if (tomb != null) {
            return Long.parseLong(tomb);
        }
        String data = redisTemplate.opsForValue().get(BannerConstants.BANNER_DATA_PREFIX + bannerId);
        if (data == null) {
            return Long.MIN_VALUE;
        }
        try {
            return objectMapper.readValue(data, BannerMessage.class).getVersion();
        } catch (Exception e) {
            log.error("解析已存 banner 数据失败 bannerId={}", bannerId, e);
            return Long.MIN_VALUE;
        }
    }

    /** 消息是否应该被消费（version 比对：新消息 version 必须大于已存 version） */
    public boolean shouldConsume(BannerMessage message) {
        return message.getVersion() != null && message.getVersion() > storedVersion(message.getId());
    }

    /** 受本次消息影响的本地缓存 key 列表（bizCode + 天），用于失效 localCache */
    public List<String> affectedLocalKeys(BannerMessage message) {
        List<String> keys = new java.util.ArrayList<>();
        for (var day : BannerConstants.coveredDays(message.getStartTime(), message.getEndTime())) {
            keys.add(localKey(message.getBizCode(), day));
        }
        return keys;
    }

    private void removeDayEntries(BannerMessage message) {
        for (var day : BannerConstants.coveredDays(message.getStartTime(), message.getEndTime())) {
            redisTemplate.opsForHash().delete(BannerConstants.bannerDayKey(message.getBizCode(), day),
                    String.valueOf(message.getId()));
        }
    }

    public static String localKey(String bizCode, java.time.LocalDate day) {
        return bizCode + ":" + day;
    }

    /** 设置过期时间 */
    private java.util.Date expireAt(java.time.LocalDate day) {
        return java.util.Date.from(day.plusDays(1).atStartOfDay(BannerConstants.SHANGHAI).toInstant());
    }
}
