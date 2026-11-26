package com.xiaozhi.mcptoolexclude.service;

import java.util.List;
import java.util.Set;

public interface McpToolExcludeService {

    Set<String> getExcludedTools(Integer roleId);

    void toggleRoleToolStatus(Integer roleId, String toolName, String serverName, boolean enabled);

    void toggleGlobalToolStatus(String toolName, String serverName, boolean enabled);

    /** 角色还没建好（id 为空或不大于 0）时为空 */
    List<String> getRoleDisabledTools(Integer roleId);

    List<String> getGlobalDisabledTools();

    void batchSetRoleExcludeTools(Integer roleId, List<String> excludeTools, String serverName);
}
