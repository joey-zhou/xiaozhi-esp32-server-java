-- 新增「启用/禁用账号」接口权限 system:user:api:update 并授予 admin 角色，幂等可重复执行。

INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`)
SELECT parent.`permissionId`, '用户状态更新接口', 'system:user:api:update', 'api', NULL, NULL, NULL, 2, '0', '1'
FROM `xiaozhi`.`sys_permission` parent
WHERE parent.`permissionKey` = 'system:user'
  AND NOT EXISTS (SELECT 1 FROM `xiaozhi`.`sys_permission` p WHERE p.`permissionKey` = 'system:user:api:update');

INSERT INTO `xiaozhi`.`sys_auth_role_permission` (`authRoleId`, `permissionId`)
SELECT authRole.`authRoleId`, permission.`permissionId`
FROM (
    SELECT 'admin' AS roleKey, 'system:user:api:update' AS permissionKey
) src
JOIN `xiaozhi`.`sys_auth_role` authRole ON authRole.`roleKey` = src.`roleKey`
JOIN `xiaozhi`.`sys_permission` permission ON permission.`permissionKey` = src.`permissionKey`
LEFT JOIN `xiaozhi`.`sys_auth_role_permission` existing
    ON existing.`authRoleId` = authRole.`authRoleId`
   AND existing.`permissionId` = permission.`permissionId`
WHERE existing.`id` IS NULL;
