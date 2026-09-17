-- 补建 McpToolController 在用、此前从未入库的四个接口权限，幂等可重复执行。
-- 角色维度的三个挂在 system:role 下，授予 admin 与 user；全局工具开关只授予 admin。

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT parent.`permissionId`, src.`name`, src.`permissionKey`, 'api', NULL, NULL, NULL, src.`sort`, '0', '1'
FROM (
    SELECT 'system:role' AS parentKey, '角色MCP工具列表接口' AS name, 'system:role:mcp-tools:api:list' AS permissionKey, 15 AS sort
    UNION ALL SELECT 'system:role', '角色MCP工具更新接口', 'system:role:mcp-tools:api:update', 16
    UNION ALL SELECT 'system:role', '系统MCP工具列表接口', 'system:role:mcp-tools:api:system-global', 17
    UNION ALL SELECT 'system:config', 'MCP工具全局开关接口', 'system:config:mcpServer:api:update', 10
) src
JOIN `sys_permission` parent ON parent.`permissionKey` = src.`parentKey`
LEFT JOIN `sys_permission` existing ON existing.`permissionKey` = src.`permissionKey`
WHERE existing.`permissionId` IS NULL;

INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT authRole.`authRoleId`, permission.`permissionId`
FROM (
    SELECT 'admin' AS roleKey, 'system:role:mcp-tools:api:list' AS permissionKey
    UNION ALL SELECT 'admin', 'system:role:mcp-tools:api:update'
    UNION ALL SELECT 'admin', 'system:role:mcp-tools:api:system-global'
    UNION ALL SELECT 'admin', 'system:config:mcpServer:api:update'
    UNION ALL SELECT 'user', 'system:role:mcp-tools:api:list'
    UNION ALL SELECT 'user', 'system:role:mcp-tools:api:update'
    UNION ALL SELECT 'user', 'system:role:mcp-tools:api:system-global'
) src
JOIN `sys_auth_role` authRole ON authRole.`roleKey` = src.`roleKey`
JOIN `sys_permission` permission ON permission.`permissionKey` = src.`permissionKey`
LEFT JOIN `sys_auth_role_permission` existing
    ON existing.`authRoleId` = authRole.`authRoleId`
   AND existing.`permissionId` = permission.`permissionId`
WHERE existing.`id` IS NULL;
