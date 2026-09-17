-- 清理前端路由已经没有对应页面的菜单权限（system:drama*、system:roles、system:message、system:setting:config）
DELETE arp FROM `sys_auth_role_permission` arp
JOIN `sys_permission` p ON p.permissionId = arp.permissionId
WHERE p.permissionKey IN ('system:roles', 'system:message', 'system:setting:config', 'system:drama', 'system:dramaPlay')
   OR p.permissionKey LIKE 'system:drama:%';

DELETE FROM `sys_permission`
WHERE permissionKey IN ('system:roles', 'system:message', 'system:setting:config', 'system:drama', 'system:dramaPlay')
   OR permissionKey LIKE 'system:drama:%';
