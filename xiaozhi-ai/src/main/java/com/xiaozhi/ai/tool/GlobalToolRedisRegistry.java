package com.xiaozhi.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统全局工具元数据 Redis 注册表。
 * <p>
 * 解决跨进程可见性问题：{@link ToolsGlobalRegistry.GlobalFunction} 的 Bean 仅在 dialogue 进程注册，
 * 而 server 进程需要在前端"排除工具"界面上展示这些工具。此注册表由 dialogue 在启动时写入 Redis，
 * server 进程查询时读取。
 * <p>
 * Redis Key: {@value #REDIS_KEY}
 * Value: JSON 数组 {@code [{"name":"...","description":"..."}]}
 */
@Slf4j
@Component
public class GlobalToolRedisRegistry {

    private static final String REDIS_KEY = "xiaozhi:system-global-tools";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将当前进程可见的 GlobalFunction 元数据发布到 Redis，供其他进程读取。
     */
    public void publish(List<ToolSummary> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        try {
            String json = JsonUtil.toJson(tools);
            stringRedisTemplate.opsForValue().set(REDIS_KEY, json);
            log.info("已发布系统全局工具元数据到 Redis，数量: {}", tools.size());
        } catch (Exception e) {
            log.warn("发布系统全局工具元数据到 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 读取全部系统全局工具元数据；若不存在返回空列表。
     */
    public List<ToolSummary> getAll() {
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_KEY);
            if (json == null || json.isEmpty()) {
                return List.of();
            }
            List<ToolSummary> list = JsonUtil.fromJson(json, new TypeReference<List<ToolSummary>>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("从 Redis 读取系统全局工具元数据失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 工具摘要：仅包含前端"排除工具"界面所需的名称和描述。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ToolSummary {
        private String name;
        private String description;
    }
}
