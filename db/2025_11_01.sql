ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `wxOpenId` VARCHAR(100) NULL COMMENT '微信OpenId';
ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `wxUnionId` VARCHAR(100) NULL COMMENT '微信UnionId';
ALTER TABLE `xiaozhi`.`sys_user` ADD COLUMN `roleId` int unsigned NOT NULL DEFAULT 2 COMMENT '角色ID';

-- 创建权限表
DROP TABLE IF EXISTS `xiaozhi`.`sys_permission`;
CREATE TABLE `xiaozhi`.`sys_permission` (
  `permissionId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `parentId` int unsigned DEFAULT NULL COMMENT '父权限ID',
  `name` varchar(100) NOT NULL COMMENT '权限名称',
  `permissionKey` varchar(100) NOT NULL COMMENT '权限标识',
  `permissionType` enum('menu','button','api') NOT NULL COMMENT '权限类型：菜单、按钮、接口',
  `path` varchar(255) DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件路径',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序',
  `visible` enum('1','0') DEFAULT '1' COMMENT '是否可见(1可见 0隐藏)',
  `status` enum('1','0') DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`permissionId`),
  UNIQUE KEY `uk_permission_key` (`permissionKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 创建角色表
DROP TABLE IF EXISTS `xiaozhi`.`sys_auth_role`;
CREATE TABLE `xiaozhi`.`sys_auth_role` (
  `roleId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `roleName` varchar(100) NOT NULL COMMENT '角色名称',
  `roleKey` varchar(100) NOT NULL COMMENT '角色标识',
  `description` varchar(500) DEFAULT NULL COMMENT '角色描述',
  `status` enum('1','0') DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`roleId`),
  UNIQUE KEY `uk_role_key` (`roleKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限角色表';

-- 创建角色-权限关联表
DROP TABLE IF EXISTS `xiaozhi`.`sys_role_permission`;
CREATE TABLE `xiaozhi`.`sys_role_permission` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `roleId` int unsigned NOT NULL COMMENT '角色ID',
  `permissionId` int unsigned NOT NULL COMMENT '权限ID',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`roleId`,`permissionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';


-- 插入菜单权限
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
-- 主菜单
(NULL, 'Dashboard', 'system:dashboard', 'menu', '/dashboard', 'page/Dashboard', 'dashboard', 1, '1', '1'),
(NULL, '用户管理', 'system:user', 'menu', '/user', 'page/User', 'team', 2, '1', '1'),
(NULL, '设备管理', 'system:device', 'menu', '/device', 'page/Device', 'robot', 3, '1', '1'),
(NULL, '智能体', 'system:agents', 'menu', '/agents', 'page/user/Agents', 'robot', 4, '1', '1'),
(NULL, '对话管理', 'system:message', 'menu', '/message', 'page/Message', 'message', 5, '1', '1'),
(NULL, '角色配置', 'system:role', 'menu', '/role', 'page/Role', 'user-add', 6, '1', '1'),
(NULL, '提示词模板管理', 'system:prompt-template', 'menu', '/prompt-template', 'page/PromptTemplate', 'snippets', 7, '0', '1'),
(NULL, '配置管理', 'system:config', 'menu', '/config', 'common/PageView', 'setting', 8, '1', '1'),
(NULL, '设置', 'system:setting', 'menu', '/setting', 'common/PageView', 'setting', 9, '1', '1');

-- 配置管理子菜单
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(8, '模型配置', 'system:config:model', 'menu', '/config/model', 'page/config/ModelConfig', NULL, 1, '1', '1'),
(8, '智能体管理', 'system:config:agent', 'menu', '/config/agent', 'page/config/Agent', NULL, 2, '1', '1'),
(8, '语音识别配置', 'system:config:stt', 'menu', '/config/stt', 'page/config/SttConfig', NULL, 3, '1', '1'),
(8, '语音合成配置', 'system:config:tts', 'menu', '/config/tts', 'page/config/TtsConfig', NULL, 4, '1', '1');

-- 设置子菜单
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(9, '个人中心', 'system:setting:account', 'menu', '/setting/account', 'page/setting/Account', NULL, 1, '1', '1'),
(9, '个人设置', 'system:setting:config', 'menu', '/setting/config', 'page/setting/Config', NULL, 2, '1', '1');

-- 插入角色
INSERT INTO `xiaozhi`.`sys_auth_role` (`roleName`, `roleKey`, `description`, `status`) VALUES
('管理员', 'admin', '系统管理员，拥有所有权限', '1'),
('普通用户', 'user', '普通用户，拥有基本操作权限', '1');

-- 管理员角色权限（所有权限）
INSERT INTO `xiaozhi`.`sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `xiaozhi`.`sys_permission`;

-- 将admin用户设为管理员角色
UPDATE `xiaozhi`.`sys_user` SET `roleId` = 1 WHERE `username` = 'admin';

-- 将其他用户设为普通用户角色
UPDATE `xiaozhi`.`sys_user` SET `roleId` = 2 WHERE `username` != 'admin';
