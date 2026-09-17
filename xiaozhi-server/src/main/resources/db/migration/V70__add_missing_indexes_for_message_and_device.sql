-- sys_message：会话列表按 createTime 排序/过滤，补索引
SET @add_idx = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `sys_message` ADD INDEX `createTime` (`createTime`)', 'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_message' AND INDEX_NAME='createTime');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_device：角色列表的设备数子查询、设备列表按角色过滤都按 roleId 条件查，补索引
SET @add_idx = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `sys_device` ADD INDEX `roleId` (`roleId`)', 'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_device' AND INDEX_NAME='roleId');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
