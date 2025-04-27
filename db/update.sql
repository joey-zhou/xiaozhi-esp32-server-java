ALTER TABLE `xiaozhi`.`sys_role` 
ADD COLUMN `isDefault` enum('1','0') DEFAULT '0' COMMENT '是否默认角色：1-是，0-否' AFTER `state`;