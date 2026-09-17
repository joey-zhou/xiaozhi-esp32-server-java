-- sys_code.email 实际同时装邮箱和手机号：手机号验证码（发送/登录/找回密码）走的也是这一列，
-- 列名与语义永久不符，后续按 email 列做的统计、唯一性约束、数据迁移都会把手机号账号算错。
-- 改名为 account（收码账号：邮箱或手机号共用一列），设备激活场景该列仍为 NULL，用来区分两半数据。
-- 只改列名不动数据，存量行原样保留。

SET @has_old_column = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_code' AND COLUMN_NAME = 'email');
SET @sql = IF(@has_old_column > 0,
    'ALTER TABLE `sys_code` CHANGE COLUMN `email` `account` varchar(100) DEFAULT NULL COMMENT ''收码账号：邮箱或手机号，设备码为NULL''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- CHANGE COLUMN 不会跟着改索引名，留着叫 email 的索引就是下一次误读的来源
SET @has_old_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_code' AND INDEX_NAME = 'email');
SET @has_new_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_code' AND INDEX_NAME = 'account');
SET @sql = IF(@has_old_index > 0 AND @has_new_index = 0,
    'ALTER TABLE `sys_code` RENAME INDEX `email` TO `account`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
