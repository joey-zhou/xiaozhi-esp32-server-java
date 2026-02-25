-- 删除 sys_device 表的 lastLogin 字段
-- 该字段已废弃，设备最后活跃时间改用 updateTime 字段替代
-- 执行时间：2026-02-25
ALTER TABLE `xiaozhi`.`sys_device` DROP COLUMN `lastLogin`;
