package com.xiaozhi.common.config;

/**
 * 缓存名的唯一定义处。
 * <p>
 * {@link RedisCacheConfig} 的 TTL 配置与各 Service 的 {@code @Cacheable}/{@code getCache} 都引用这里的常量：
 * 两边各写一遍字面量时，改一处忘改另一处会让 TTL 配置挂在一个没人用的名字上，
 * 实际的 key 悄悄退回默认 TTL。新增缓存名要同时在 {@link RedisCacheConfig} 里配 TTL，
 * 漏配由 {@code RedisCacheConfigTest} 钉住。
 */
public final class CacheNames {

    public static final String DEVICE = "XiaoZhi:Device";

    public static final String PERMISSION = "XiaoZhi:Permission";

    public static final String USER = "XiaoZhi:User";

    public static final String SYS_CONFIG = "XiaoZhi:SysConfig";

    public static final String MCP_TOOL_EXCLUDE = "XiaoZhi:McpToolExclude";

    public static final String ROLE = "XiaoZhi:Role";

    private CacheNames() {
    }
}
