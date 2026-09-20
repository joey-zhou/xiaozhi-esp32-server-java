package com.xiaozhi.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * 全仓唯一的取时入口。入库的时间列都是不带时区的 DATETIME，存的是应用所在时区的墙上时间，
 * 所以「现在几点」和 Instant 与 LocalDateTime 的互转必须出自同一个时钟、同一个时区，
 * 业务代码不得自己调 {@code LocalDateTime.now()}、{@code Instant.now()} 或 {@code ZoneId.systemDefault()}。
 *
 * <p>算耗时、超时用 {@code System.nanoTime()} 取起点、{@link #elapsedMillis} 取经过时间，不要拿两次墙上时间相减。
 */
public final class DateUtils {

    private static volatile Clock clock = Clock.systemDefaultZone();

    private DateUtils() {
    }

    /** 入库与比较用的当前时间 */
    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static Instant instant() {
        return clock.instant();
    }

    public static long millis() {
        return clock.millis();
    }

    /**
     * 自 {@code System.nanoTime()} 取的起点起经过的毫秒数。算耗时与超时用它而不是两次墙上时间相减，
     * 系统对时把时钟拨快拨慢都不影响结果
     */
    public static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    public static ZoneId zone() {
        return clock.getZone();
    }

    /** 应用所在时区此刻相对 UTC 的偏移，夏令时期间与标准时间不同 */
    public static ZoneOffset offset() {
        return clock.getZone().getRules().getOffset(clock.instant());
    }

    public static LocalDateTime toDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, clock.getZone());
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(clock.getZone()).toInstant();
    }

    /** 仅供测试固定时间，用完必须 {@link #reset()} */
    public static void use(Clock fixed) {
        clock = fixed;
    }

    public static void reset() {
        clock = Clock.systemDefaultZone();
    }
}
