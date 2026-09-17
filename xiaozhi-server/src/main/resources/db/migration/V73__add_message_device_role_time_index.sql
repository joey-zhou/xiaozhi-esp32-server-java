-- sys_message：对话取历史上下文、回填音频/截断应答都按 deviceId+roleId+createTime 查，补联合索引
SET @add_idx = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `sys_message` ADD INDEX `deviceId_roleId_createTime` (`deviceId`, `roleId`, `createTime`)', 'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_message' AND INDEX_NAME='deviceId_roleId_createTime');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
