package com.xiaozhi.common.config;

import com.xiaozhi.utils.DateUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 让数据库时钟与应用时钟读出同一个墙上时间。时间列都是不带时区的 DATETIME，
 * 应用写入的是 {@link DateUtils} 的本地时间，而列默认值与 ON UPDATE CURRENT_TIMESTAMP 由数据库按会话时区写入，
 * 数据库所在时区与应用不同（容器默认 UTC、云数据库）时两者会差出整小时。
 *
 * <p>会话时区用偏移量而不是时区名：时区名要求 MySQL 装载过时区表，没装载时建连会直接失败。
 * 偏移量在启动时取一次，有夏令时的时区在切换后要重启才跟上。
 */
@Slf4j
@Configuration
public class DatabaseClockConfig {

    /** 超过这个差值就不是网络往返与对时误差能解释的了 */
    static final Duration MAX_DRIFT = Duration.ofMinutes(1);

    @Bean
    public static BeanPostProcessor sessionTimeZoneAligner() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // 运维显式配了建连语句的不覆盖，对不上由启动自检报出来
                if (bean instanceof HikariDataSource dataSource && dataSource.getConnectionInitSql() == null) {
                    dataSource.setConnectionInitSql(sessionTimeZoneSql(DateUtils.offset()));
                }
                return bean;
            }
        };
    }

    @Bean
    public ApplicationRunner databaseClockCheck(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT NOW()")) {
                resultSet.next();
                LocalDateTime databaseNow = resultSet.getObject(1, LocalDateTime.class);
                LocalDateTime appNow = DateUtils.now();
                if (drifted(databaseNow, appNow)) {
                    log.error("数据库时钟与应用时钟对不上 - 数据库 {}，应用 {}。列默认值与自动更新时间由数据库写入，"
                            + "会与应用写入的时间差出这么多；请确认连接池建连时执行了 SET time_zone，且两台机器已对时",
                            databaseNow, appNow);
                }
            } catch (SQLException e) {
                log.warn("数据库时钟自检未能执行", e);
            }
        };
    }

    static String sessionTimeZoneSql(ZoneOffset offset) {
        // MySQL 不认 Z，UTC 要写成 +00:00
        String id = ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId();
        return "SET time_zone = '" + id + "'";
    }

    static boolean drifted(LocalDateTime databaseNow, LocalDateTime appNow) {
        return Duration.between(databaseNow, appNow).abs().compareTo(MAX_DRIFT) > 0;
    }
}
