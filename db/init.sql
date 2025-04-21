-- 在文件顶部添加以下语句
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 创建本地用户并设置密码（使用mysql_native_password插件）
CREATE USER IF NOT EXISTS 'xiaozhi'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';

-- 创建远程用户并设置密码（使用mysql_native_password插件）
CREATE USER IF NOT EXISTS 'xiaozhi'@'%' IDENTIFIED WITH mysql_native_password BY '123456';

-- 仅授予本地用户对 xiaozhi 数据库的所有权限
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'localhost';

-- 仅授予远程用户对 xiaozhi 数据库的所有权限
GRANT ALL PRIVILEGES ON xiaozhi.* TO 'xiaozhi'@'%';

-- 刷新权限以使更改生效
FLUSH PRIVILEGES;

-- 查看用户权限
SHOW GRANTS FOR 'xiaozhi'@'localhost';
SHOW GRANTS FOR 'xiaozhi'@'%';

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `xiaozhi` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- xiaozhi.sys_user definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_user`;
CREATE TABLE `xiaozhi`.`sys_user` (
  `userId` int unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `tel` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '1-正常 0-禁用',
  `loginIp` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isAdmin` enum('1','0') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `loginTime` datetime DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP,
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`userId`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert admin user only if it doesn't exist
INSERT INTO xiaozhi.sys_user (username, password, state, isAdmin, name, createTime, updateTime)
VALUES ('admin', '11cd9c061d614dcf37ec60c44c11d2ad', '1', '1', '小智', '2025-03-09 18:32:29', '2025-03-09 18:32:35');

update sys_user set name = '小智' where username = 'admin';

-- xiaozhi.sys_device definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_device`;
CREATE TABLE `xiaozhi`.`sys_device` (
  `deviceId` varchar(255) NOT NULL COMMENT '设备ID，主键',
  `deviceName` varchar(100) NOT NULL COMMENT '设备名称',
  `modelId` int DEFAULT NULL COMMENT '模型ID',
  `sttId` int DEFAULT NULL COMMENT 'STT服务ID',
  `roleId` int unsigned DEFAULT NULL COMMENT '角色ID，主键',
  `ip` varchar(45) DEFAULT NULL COMMENT 'IP地址',
  `wifiName` varchar(100) DEFAULT NULL COMMENT 'WiFi名称',
  `chipModelName` varchar(100) DEFAULT NULL COMMENT '芯片型号',
  `version` varchar(50) DEFAULT NULL COMMENT '固件版本',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '设备状态：1-在线，0-离线',
  `userId` int NOT NULL COMMENT '创建人',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `lastLogin` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后登录时间',
  PRIMARY KEY (`deviceId`),
  KEY `deviceName` (`deviceName`),
  KEY `userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信息表';

-- xiaozhi.sys_message definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_message`;
CREATE TABLE `xiaozhi`.`sys_message` (
  `messageId` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键，自增',
  `deviceId` varchar(30) NOT NULL COMMENT '设备ID',
  `sessionId` varchar(100) NOT NULL COMMENT '会话ID',
  `sender` enum('user','assistant') NOT NULL COMMENT '消息发送方：user-用户，assistant-人工智能',
  `roleId` bigint COMMENT 'AI扮演的角色ID',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
  `audioPath` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '语音文件路径',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '状态：1-有效，0-删除',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
  PRIMARY KEY (`messageId`),
  KEY `deviceId` (`deviceId`),
  KEY `sessionId` (`sessionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人与AI对话消息表';

-- xiaozhi.sys_role definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_role`;
CREATE TABLE `xiaozhi`.`sys_role` (
  `roleId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '角色ID，主键',
  `roleName` varchar(100) NOT NULL COMMENT '角色名称',
  `roleDesc` TEXT DEFAULT NULL COMMENT '角色描述',
  `ttsId` int DEFAULT NULL COMMENT 'TTS服务ID',
  `voiceName` varchar(100) NOT NULL COMMENT '角色语音名称',
  `state` enum('1','0') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `userId` int NOT NULL COMMENT '创建人',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`roleId`),
  KEY `userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- xiaozhi.sys_code definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_code`;
CREATE TABLE `xiaozhi`.`sys_code` (
  `codeId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(100) NOT NULL COMMENT '验证码',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `deviceId` varchar(30) DEFAULT NULL COMMENT '设备ID',
  `sessionId` varchar(100) DEFAULT NULL COMMENT 'sessionID',
  `audioPath` text COMMENT '语音文件路径',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`codeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- xiaozhi.sys_config definition
DROP TABLE IF EXISTS `xiaozhi`.`sys_config`;
CREATE TABLE `xiaozhi`.`sys_config` (
  `configId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '配置ID，主键',
  `userId` int NOT NULL COMMENT '创建用户ID',
  `configType` varchar(30) NOT NULL COMMENT '配置类型(llm, stt, tts等)',
  `provider` varchar(30) NOT NULL COMMENT '服务提供商(openai, vosk, aliyun, tencent等)',
  `configName` varchar(50) DEFAULT NULL COMMENT '配置名称',
  `configDesc` TEXT DEFAULT NULL COMMENT '配置描述',
  `appId` varchar(100) DEFAULT NULL COMMENT 'APP ID',
  `apiKey` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `apiSecret` varchar(255) DEFAULT NULL COMMENT 'API密钥',
  `apiUrl` varchar(255) DEFAULT NULL COMMENT 'API地址',
  `isDefault` enum('1','0') DEFAULT '0' COMMENT '是否为默认配置: 1-是, 0-否',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`configId`),
  KEY `userId` (`userId`),
  KEY `configType` (`configType`),
  KEY `provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表(模型、语音识别、语音合成等)';
