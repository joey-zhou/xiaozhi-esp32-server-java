-- 对话记忆统一：不再区分窗口/摘要/长期三种记忆类型，所有角色都是同一套对话记忆。
-- Web 聊天的摘要按会话隔离：sys_summary 增加 sessionId，设备端摘要该列为空。
-- 结构变更都是存在才跳过，手工改过的库不报错。

SET @has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_summary' AND COLUMN_NAME = 'sessionId'
);
SET @ddl = IF(@has_column = 0,
    'ALTER TABLE `sys_summary` ADD COLUMN `sessionId` varchar(255) NULL COMMENT ''Web 会话ID，设备端为空'' AFTER `roleId`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_summary' AND INDEX_NAME = 'idx_summary_session'
);
SET @ddl = IF(@has_index = 0,
    'ALTER TABLE `sys_summary` ADD INDEX `idx_summary_session` (`sessionId`, `createTime`)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'memoryType'
);
SET @ddl = IF(@has_column = 1,
    'ALTER TABLE `sys_role` DROP COLUMN `memoryType`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
