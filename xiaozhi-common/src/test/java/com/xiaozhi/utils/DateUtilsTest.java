package com.xiaozhi.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    private static final Instant MOMENT = Instant.parse("2026-03-08T18:30:00Z");

    @AfterEach
    void tearDown() {
        DateUtils.reset();
    }

    @Test
    void everyAccessorReadsTheSameClockAndZone() {
        DateUtils.use(Clock.fixed(MOMENT, ZoneId.of("Asia/Shanghai")));

        assertThat(DateUtils.instant()).isEqualTo(MOMENT);
        assertThat(DateUtils.millis()).isEqualTo(MOMENT.toEpochMilli());
        // 同一时刻在东八区已经是第二天凌晨
        assertThat(DateUtils.now()).isEqualTo(LocalDateTime.of(2026, 3, 9, 2, 30));
        assertThat(DateUtils.today()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void conversionsUseTheClockZoneNotTheJvmDefault() {
        DateUtils.use(Clock.fixed(MOMENT, ZoneId.of("America/Los_Angeles")));

        LocalDateTime dateTime = DateUtils.toDateTime(MOMENT);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(2026, 3, 8, 11, 30));
        assertThat(DateUtils.toInstant(dateTime)).isEqualTo(MOMENT);
    }

    @Test
    void offsetFollowsDaylightSaving() {
        DateUtils.use(Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneId.of("America/Los_Angeles")));
        assertThat(DateUtils.offset()).isEqualTo(ZoneOffset.ofHours(-7));

        DateUtils.use(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("America/Los_Angeles")));
        assertThat(DateUtils.offset()).isEqualTo(ZoneOffset.ofHours(-8));
    }

    @Test
    void elapsedMillisCountsFromAMonotonicStart() {
        long start = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(250);

        assertThat(DateUtils.elapsedMillis(start)).isBetween(250L, 2000L);
    }

    @Test
    void conversionsPassNullThrough() {
        assertThat(DateUtils.toDateTime(null)).isNull();
        assertThat(DateUtils.toInstant(null)).isNull();
    }

    @Test
    void resetReturnsToTheSystemClock() {
        DateUtils.use(Clock.fixed(MOMENT, ZoneId.of("UTC")));

        DateUtils.reset();

        assertThat(DateUtils.instant()).isAfter(MOMENT);
        assertThat(DateUtils.zone()).isEqualTo(ZoneId.systemDefault());
    }
}
