-- 角色参数默认值以 RoleBO 为准（temperature 0.7、topP 0.9、语音阈值 0.5、静音阈值 0.3、能量阈值 0.01、静音 800ms），
-- 只有 topP 的列默认（1.0）与它不一致。已有角色的取值不动。MODIFY 重复执行结果不变。
ALTER TABLE `sys_role`
  MODIFY COLUMN `topP` double DEFAULT 0.9 COMMENT 'Top-P参数';
