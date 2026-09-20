package com.xiaozhi.common.config;

import com.xiaozhi.utils.DateUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseClockConfigTest {

    @AfterEach
    void resetClock() {
        DateUtils.reset();
    }

    @Test
    void sessionTimeZoneIsWrittenAsAnOffsetMysqlAccepts() {
        assertThat(DatabaseClockConfig.sessionTimeZoneSql(ZoneOffset.ofHours(8))).isEqualTo("SET time_zone = '+08:00'");
        assertThat(DatabaseClockConfig.sessionTimeZoneSql(ZoneOffset.ofHoursMinutes(-3, -30))).isEqualTo("SET time_zone = '-03:30'");
        // MySQL 不认 Z
        assertThat(DatabaseClockConfig.sessionTimeZoneSql(ZoneOffset.UTC)).isEqualTo("SET time_zone = '+00:00'");
    }

    @Test
    void alignerUsesTheOffsetInEffectNowIncludingDaylightSaving() {
        // 2026-07-01 洛杉矶在夏令时，偏移是 -07:00 而不是标准时间的 -08:00
        DateUtils.use(Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneId.of("America/Los_Angeles")));
        HikariDataSource dataSource = new HikariDataSource();

        aligner().postProcessAfterInitialization(dataSource, "dataSource");

        assertThat(dataSource.getConnectionInitSql()).isEqualTo("SET time_zone = '-07:00'");
    }

    @Test
    void alignerKeepsAnExplicitlyConfiguredInitSql() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setConnectionInitSql("SET NAMES utf8mb4");

        aligner().postProcessAfterInitialization(dataSource, "dataSource");

        assertThat(dataSource.getConnectionInitSql()).isEqualTo("SET NAMES utf8mb4");
    }

    @Test
    void alignerLeavesOtherBeansAlone() {
        Object bean = new Object();

        assertThat(aligner().postProcessAfterInitialization(bean, "anything")).isSameAs(bean);
    }

    @Test
    void driftToleratesRoundTripButNotAZoneGap() {
        LocalDateTime appNow = LocalDateTime.of(2026, 9, 19, 12, 0, 0);

        assertThat(DatabaseClockConfig.drifted(appNow.minusSeconds(20), appNow)).isFalse();
        assertThat(DatabaseClockConfig.drifted(appNow.plusSeconds(20), appNow)).isFalse();
        assertThat(DatabaseClockConfig.drifted(appNow.minusHours(8), appNow)).isTrue();
        assertThat(DatabaseClockConfig.drifted(appNow.plusHours(7), appNow)).isTrue();
    }

    private static BeanPostProcessor aligner() {
        return DatabaseClockConfig.sessionTimeZoneAligner();
    }
}
