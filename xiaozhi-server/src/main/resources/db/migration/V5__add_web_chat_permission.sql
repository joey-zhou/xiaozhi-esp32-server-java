-- Web 聊天菜单权限
INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
VALUES (NULL, 'Web 聊天', 'system:chat', 'menu', '/chat', 'page/Chat', 'message', 12, '1', '1');

-- Web 聊天 API 子权限（挂在 system:chat 下）
INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '开启会话', 'system:chat:api:open', 'api', NULL, NULL, NULL, 1, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:chat';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '流式聊天', 'system:chat:api:stream', 'api', NULL, NULL, NULL, 2, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:chat';

INSERT INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT permissionId, '关闭会话', 'system:chat:api:close', 'api', NULL, NULL, NULL, 3, '0', '1'
FROM `sys_permission` WHERE `permissionKey` = 'system:chat';

-- 管理员角色自动授予 Web 聊天所有权限
INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission`
WHERE `permissionKey` IN ('system:chat', 'system:chat:api:open', 'system:chat:api:stream', 'system:chat:api:close')
AND permissionId NOT IN (SELECT permissionId FROM `sys_auth_role_permission` WHERE `authRoleId` = 1);

-- 普通用户角色也授予 Web 聊天权限（聊天是基础功能）
INSERT INTO `sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT 2, permissionId FROM `sys_permission`
WHERE `permissionKey` IN ('system:chat', 'system:chat:api:open', 'system:chat:api:stream', 'system:chat:api:close')
AND permissionId NOT IN (SELECT permissionId FROM `sys_auth_role_permission` WHERE `authRoleId` = 2);
