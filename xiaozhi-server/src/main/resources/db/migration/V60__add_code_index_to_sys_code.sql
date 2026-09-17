-- sys_code：设备绑定按 code 定位、发码前按 code 探测占用，两条路径都只带 code + createTime 条件
SET @add_idx = (SELECT IF(COUNT(*)=0,
    'ALTER TABLE `sys_code` ADD INDEX `code_createTime` (`code`, `createTime`)', 'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_code' AND INDEX_NAME='code_createTime');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
