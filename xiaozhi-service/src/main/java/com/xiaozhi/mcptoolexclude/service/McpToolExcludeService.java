package com.xiaozhi.mcptoolexclude.service;

import java.util.List;
import java.util.Set;

public interface McpToolExcludeService {

    String CACHE_NAME = "XiaoZhi:McpToolExclude";

    Set<String> getExcludedTools(Integer roleId);

    void toggleRoleToolStatus(Integer roleId, String toolName, String serverName, boolean enabled);

    void toggleGlobalToolStatus(String toolName, String serverName, boolean enabled);

    List<String> getRoleDisabledTools(Integer roleId);

    List<String> getGlobalDisabledTools();

    void batchSetRoleExcludeTools(Integer roleId, List<String> excludeTools, String serverName);
}
