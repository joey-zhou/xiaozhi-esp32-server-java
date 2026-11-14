package com.xiaozhi.mcptoolexclude.support;

/** MCP_TOOL_EXCLUDE 缓存的 key 拼装规则，读写两侧共用；写路径整份清空，不按 key 淘汰。 */
public final class McpToolExcludeCacheKeys {

    public static final String GLOBAL_DISABLED = "global_disabled";

    private McpToolExcludeCacheKeys() {}

    public static String excludedTools(Integer roleId) {
        return "excluded_tools:" + roleId;
    }

    public static String roleDisabled(Integer roleId) {
        return "role_disabled:" + roleId;
    }
}
