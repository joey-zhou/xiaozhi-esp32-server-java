ALTER TABLE `xiaozhi`.`sys_config` 
ADD COLUMN `ak` varchar(255) DEFAULT NULL COMMENT 'Access Key' AFTER `apiSecret`,
ADD COLUMN `sk` varchar(255) DEFAULT NULL COMMENT 'Secret Key' AFTER `ak`;