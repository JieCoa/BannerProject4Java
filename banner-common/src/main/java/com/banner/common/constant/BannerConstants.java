package com.banner.common.constant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局常量
 */
public final class BannerConstants {

    private BannerConstants() {
    }

    /** 统一时区（示例数据与缓存 key 的"天"都以北京时间为准） */
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** Kafka topic：banner 变更消息 */
    public static final String BANNER_TOPIC = "banner-topic";

    /** Redis 缓存 key 前缀：banner:{bizCode}:{yyyyMMdd} */
    public static final String BANNER_KEY_PREFIX = "banner:";

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 第一个 Key：banner:{bannerId} */
    public static String bannerKey(Long bannerId) {
        return BANNER_KEY_PREFIX + bannerId;
    }

    /** 第二个 Key：banner:{bizCode}:{yyyyMMdd}，Hash 形式的 Banner Map */
    public static String bannerBusinessDayKey(String bizCode, LocalDate day) {
        return BANNER_KEY_PREFIX + bizCode + ":" + DAY_FORMATTER.format(day);
    }

    /** 第三个 Key：banner:{bannerId}:bucketIndex:{n} */
    public static String bannerBucketKey(Long bannerId, int bucketIndex) {
        return BANNER_KEY_PREFIX + bannerId + ":bucketIndex:" + bucketIndex;
    }

    /** banner 生效区间 [start, end] 覆盖到的每一天 */
    public static List<LocalDate> coveredDays(LocalDateTime start, LocalDateTime end) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate last = end.toLocalDate();
        while (!cursor.isAfter(last)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
