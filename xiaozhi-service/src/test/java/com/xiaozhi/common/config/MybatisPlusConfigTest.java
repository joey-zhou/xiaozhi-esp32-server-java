package com.xiaozhi.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.utils.DateUtils;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动填充的时间戳。全仓的写法是「先查出 DO、改几个字段、updateById」，
 * 这时 DO 上的 updateTime 是查出来的旧值，填充必须覆盖它，否则旧时间被原样写回，
 * 依赖 updateTime 变化的地方都会失效：存储客户端按 OSS 配置的 updateTime 决定要不要重建，
 * 会话等列表按它排序。
 */
class MybatisPlusConfigTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);

    private final MetaObjectHandler handler = new MybatisPlusConfig().metaObjectHandler();

    /** 严格填充按实体的表元数据判断哪些字段要填，启动时由 MyBatis-Plus 注册，单测里按生产的列名规则手动补上 */
    @BeforeAll
    static void registerTableInfo() {
        MybatisPlusTestHelper.initTableInfo(ConfigDO.class);
    }

    @BeforeEach
    void fixClock() {
        DateUtils.use(Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void resetClock() {
        DateUtils.reset();
    }

    @Test
    void updateOverwritesTheUpdateTimeLoadedFromTheDatabase() {
        ConfigDO loaded = new ConfigDO();
        loaded.setCreateTime(NOW.minusDays(30));
        loaded.setUpdateTime(NOW.minusDays(3));

        handler.updateFill(SystemMetaObject.forObject(loaded));

        assertThat(loaded.getUpdateTime()).isEqualTo(NOW);
        assertThat(loaded.getCreateTime()).as("更新不能动创建时间").isEqualTo(NOW.minusDays(30));
    }

    @Test
    void insertKeepsTimestampsTheCallerSetExplicitly() {
        // 批量落库的消息会把 updateTime 对齐到自己的 createTime，插入时不能被覆盖
        ConfigDO fresh = new ConfigDO();
        fresh.setCreateTime(NOW.minusMinutes(5));
        fresh.setUpdateTime(NOW.minusMinutes(5));

        handler.insertFill(SystemMetaObject.forObject(fresh));

        assertThat(fresh.getCreateTime()).isEqualTo(NOW.minusMinutes(5));
        assertThat(fresh.getUpdateTime()).isEqualTo(NOW.minusMinutes(5));
    }

    @Test
    void insertFillsMissingTimestamps() {
        ConfigDO fresh = new ConfigDO();

        handler.insertFill(SystemMetaObject.forObject(fresh));

        assertThat(fresh.getCreateTime()).isEqualTo(NOW);
        assertThat(fresh.getUpdateTime()).isEqualTo(NOW);
    }
}
