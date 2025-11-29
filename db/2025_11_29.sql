-- 2025-11-29 数据库变更
-- 为 sys_role 表添加 temperature 和 topP 字段

-- 添加温度参数字段
ALTER TABLE `xiaozhi`.`sys_role`
ADD COLUMN `temperature` DOUBLE DEFAULT 0.7 COMMENT '温度参数，控制输出的随机性' AFTER `sttId`;

-- 添加 Top-P 参数字段
ALTER TABLE `xiaozhi`.`sys_role`
ADD COLUMN `topP` DOUBLE DEFAULT 0.9 COMMENT 'Top-P参数，控制输出的多样性' AFTER `temperature`;
