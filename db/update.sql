ALTER TABLE `xiaozhi`.`sys_config` 
ADD COLUMN `ak` varchar(255) DEFAULT NULL COMMENT 'Access Key' AFTER `apiSecret`,
ADD COLUMN `sk` varchar(255) DEFAULT NULL COMMENT 'Secret Key' AFTER `ak`;
ALTER TABLE xiaozhi.sys_config MODIFY COLUMN configName varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '配置名称';