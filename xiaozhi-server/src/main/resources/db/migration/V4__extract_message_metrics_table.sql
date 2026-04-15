-- 删除 sys_message 中指标列
ALTER TABLE `sys_message`
  DROP COLUMN `tokens`,
  DROP COLUMN `sttDuration`,
  DROP COLUMN `ttsDuration`,
  DROP COLUMN `ttfsTime`,
  DROP COLUMN `responseTime`;
