package com.xiaozhi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Dialogue 独立启动入口
 * <p>
 * 包含：WebSocket/AI 对话、长期记忆、MCP 工具调用。
 * 可横向扩展，通过 Redis Pub/Sub 与其他实例协作。
 * <p>
 * 数据库 Migration 仅在 xiaozhi-server 中执行（spring.flyway.enabled=false）。
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@ComponentScan(
    basePackages = {
        // xiaozhi-common
        "com.xiaozhi.common",
        "com.xiaozhi.communication",
        "com.xiaozhi.utils",
        // xiaozhi-service (dialogue 需要的部分)
        "com.xiaozhi.config",
        "com.xiaozhi.storage",
        "com.xiaozhi.device",
        "com.xiaozhi.mcptoolexclude",
        "com.xiaozhi.message",
        "com.xiaozhi.monitoring",
        "com.xiaozhi.role",
        "com.xiaozhi.summary",
        "com.xiaozhi.token",
        "com.xiaozhi.task",
        // xiaozhi-ai
        "com.xiaozhi.ai",
        // xiaozhi-dialogue
        "com.xiaozhi.dialogue",
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
@MapperScan({
    "com.xiaozhi.config.dal.mysql.mapper",
    "com.xiaozhi.device.dal.mysql.mapper",
    "com.xiaozhi.mcptoolexclude.dal.mysql.mapper",
    "com.xiaozhi.message.dal.mysql.mapper",
    "com.xiaozhi.role.dal.mysql.mapper",
    "com.xiaozhi.summary.dal.mysql.mapper",
})
public class DialogueApplication {

    public static void main(String[] args) {
        SpringApplication.run(DialogueApplication.class, args);
    }
}
