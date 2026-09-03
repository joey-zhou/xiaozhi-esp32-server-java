-- sys_user 的 email / tel 唯一性交给数据库保证，只约束填了值的行。
-- 空串必须先规范成 NULL：唯一索引不约束 NULL 但约束 ''，不清理会让多个未填邮箱的用户互相冲突。
-- 历史重复保留 userId 最小的一条，其余置 NULL——被置空的账号仍可用用户名登录，
-- 但失去邮箱/手机号登录与找回途径，需要重新绑定。

UPDATE `sys_user` SET `email` = NULL WHERE `email` = '';
UPDATE `sys_user` SET `tel`   = NULL WHERE `tel`   = '';

UPDATE `sys_user` u
JOIN (
    SELECT `userId` FROM (
        SELECT `userId`, ROW_NUMBER() OVER (PARTITION BY `email` ORDER BY `userId`) AS rn
        FROM `sys_user` WHERE `email` IS NOT NULL
    ) ranked WHERE ranked.rn > 1
) dup ON u.`userId` = dup.`userId`
SET u.`email` = NULL;

UPDATE `sys_user` u
JOIN (
    SELECT `userId` FROM (
        SELECT `userId`, ROW_NUMBER() OVER (PARTITION BY `tel` ORDER BY `userId`) AS rn
        FROM `sys_user` WHERE `tel` IS NOT NULL
    ) ranked WHERE ranked.rn > 1
) dup ON u.`userId` = dup.`userId`
SET u.`tel` = NULL;

-- 旧的普通索引由唯一索引取代，留着是重复索引
SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'email');
SET @sql = IF(@exists_idx > 0, 'ALTER TABLE `sys_user` DROP INDEX `email`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'tel');
SET @sql = IF(@exists_idx > 0, 'ALTER TABLE `sys_user` DROP INDEX `tel`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_user_email');
SET @sql = IF(@exists_idx = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_user_email` (`email`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_user_tel');
SET @sql = IF(@exists_idx = 0, 'ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_user_tel` (`tel`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
