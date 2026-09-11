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

    /** 营销系统本地缓存的 tombstone（墓碑）前缀：banner:tomb:{bannerId}，防止乱序消息复活已删 banner */
    public static final String BANNER_TOMB_PREFIX = "banner:tomb:";

    /** 最新消息数据前缀：banner:data:{bannerId}，用于乱序比较与旧覆盖天数回滚 */
    public static final String BANNER_DATA_PREFIX = "banner:data:";

    /** 墓碑 TTL：超过该时长后乱序到达的消息也不可能再复活已删数据 */
    public static final long TOMBSTONE_TTL_HOURS = 24;

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 拼装某业务某天的缓存 key，如 banner:agri:20260910 */
    public static String bannerDayKey(String bizCode, LocalDate day) {
        return BANNER_KEY_PREFIX + bizCode + ":" + DAY_FORMATTER.format(day);
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
