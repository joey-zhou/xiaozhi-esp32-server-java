-- 补齐各页面缺失的按钮权限行，并为管理员角色补齐授权（普通用户补齐个人资料更新权限）
-- 说明：此前仅初始化了菜单与接口权限（:api:*），前端按钮按 system:xxx:action 判定，
-- 新库下管理员登录后看不到新增/编辑/删除等操作按钮。

INSERT INTO `sys_permission`
    (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT
    parent.`permissionId`,
    src.`name`,
    src.`permissionKey`,
    'button',
    NULL,
    NULL,
    NULL,
    src.`sort`,
    '0',
    '1'
FROM (
    SELECT 'system:user' AS parentKey, '导出用户' AS name, 'system:user:export' AS permissionKey, 1 AS sort
    UNION ALL SELECT 'system:device', '新增设备', 'system:device:create', 1
    UNION ALL SELECT 'system:device', '编辑设备', 'system:device:update', 2
    UNION ALL SELECT 'system:device', '删除设备', 'system:device:delete', 3
    UNION ALL SELECT 'system:device', '设备记忆', 'system:device:memory', 4
    UNION ALL SELECT 'system:role', '新增角色', 'system:role:create', 1
    UNION ALL SELECT 'system:role', '编辑角色', 'system:role:update', 2
    UNION ALL SELECT 'system:role', '删除角色', 'system:role:delete', 3
    UNION ALL SELECT 'system:role', '角色记忆', 'system:role:memory', 4
    UNION ALL SELECT 'system:role', 'MCP工具设置', 'system:role:mcp-tools', 5
    UNION ALL SELECT 'system:role', '聊天记忆删除', 'system:role:memory:chat:delete', 6
    UNION ALL SELECT 'system:role', '摘要记忆删除', 'system:role:memory:summary:delete', 7
    UNION ALL SELECT 'system:role', '记忆导出', 'system:role:memory:export', 8
    UNION ALL SELECT 'system:prompt-template', '新增模板', 'system:prompt-template:create', 1
    UNION ALL SELECT 'system:prompt-template', '编辑模板', 'system:prompt-template:update', 2
    UNION ALL SELECT 'system:prompt-template', '删除模板', 'system:prompt-template:delete', 3
    UNION ALL SELECT 'system:config', '新增配置', 'system:config:create', 1
    UNION ALL SELECT 'system:config', '编辑配置', 'system:config:update', 2
    UNION ALL SELECT 'system:config', '删除配置', 'system:config:delete', 3
    UNION ALL SELECT 'system:config:agent', '新增平台配置', 'system:config:agent:create', 1
    UNION ALL SELECT 'system:config:agent', '编辑平台配置', 'system:config:agent:update', 2
    UNION ALL SELECT 'system:setting:account', '更新个人资料', 'system:setting:account:update', 1
) src
JOIN `sys_permission` parent ON parent.`permissionKey` = src.`parentKey`
LEFT JOIN `sys_permission` existing ON existing.`permissionKey` = src.`permissionKey`
WHERE existing.`permissionId` IS NULL;

-- 管理员角色拥有全部新增按钮权限
INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT authRole.`authRoleId`, p.`permissionId`
FROM `sys_auth_role` authRole
JOIN `sys_permission` p ON p.`permissionKey` IN (
    'system:user:export',
    'system:device:create',
    'system:device:update',
    'system:device:delete',
    'system:device:memory',
    'system:role:create',
    'system:role:update',
    'system:role:delete',
    'system:role:memory',
    'system:role:mcp-tools',
    'system:role:memory:chat:delete',
    'system:role:memory:summary:delete',
    'system:role:memory:export',
    'system:prompt-template:create',
    'system:prompt-template:update',
    'system:prompt-template:delete',
    'system:config:create',
    'system:config:update',
    'system:config:delete',
    'system:config:agent:create',
    'system:config:agent:update',
    'system:setting:account:update'
)
LEFT JOIN `sys_auth_role_permission` existing
    ON existing.`authRoleId` = authRole.`authRoleId`
   AND existing.`permissionId` = p.`permissionId`
WHERE authRole.`roleKey` = 'admin'
  AND existing.`id` IS NULL;

-- 普通用户补齐个人资料更新按钮与对应接口权限
INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT authRole.`authRoleId`, p.`permissionId`
FROM `sys_auth_role` authRole
JOIN `sys_permission` p ON p.`permissionKey` IN (
    'system:setting:account:update',
    'system:setting:account:api:update'
)
LEFT JOIN `sys_auth_role_permission` existing
    ON existing.`authRoleId` = authRole.`authRoleId`
   AND existing.`permissionId` = p.`permissionId`
WHERE authRole.`roleKey` = 'user'
  AND existing.`id` IS NULL;
