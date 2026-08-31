ALTER TABLE `xiaozhi`.`sys_role`
  ADD COLUMN `inactiveTimeoutSeconds` INT NOT NULL DEFAULT 60 COMMENT '会话空闲自动结束秒数，0表示关闭' AFTER `vadSilenceMs`;
