-- sys_user_auth 是全库唯一用下划线列名的表，其余表一律驼峰（userId、createTime）。
-- 列名不一致逼得 UserAuthDO 每个字段都要写 @TableField 指列名，也没法像别的表一样继承 BaseDO。
-- 只改列名与索引名，类型、默认值、数据原样保留。

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND COLUMN_NAME = 'user_id');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_user_auth` CHANGE COLUMN `user_id` `userId` int unsigned NOT NULL COMMENT ''用户ID''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND COLUMN_NAME = 'open_id');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_user_auth` CHANGE COLUMN `open_id` `openId` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''第三方平台用户标识''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND COLUMN_NAME = 'union_id');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_user_auth` CHANGE COLUMN `union_id` `unionId` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''第三方平台开放平台标识''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND COLUMN_NAME = 'create_time');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_user_auth` CHANGE COLUMN `create_time` `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND COLUMN_NAME = 'update_time');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_user_auth` CHANGE COLUMN `update_time` `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- CHANGE COLUMN 不会跟着改索引名
SET @has_old_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND INDEX_NAME = 'uk_platform_open_id');
SET @sql = IF(@has_old_index > 0,
    'ALTER TABLE `sys_user_auth` RENAME INDEX `uk_platform_open_id` TO `uk_platform_openId`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_auth' AND INDEX_NAME = 'idx_user_id');
SET @sql = IF(@has_old_index > 0,
    'ALTER TABLE `sys_user_auth` RENAME INDEX `idx_user_id` TO `idx_userId`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
