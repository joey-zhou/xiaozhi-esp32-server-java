-- V11: 修复 enum('1','0') 列的排序 bug —— 翻转为 enum('0','1')
--
-- 背景：多个布尔语义列（isDefault / state / status 等）历史上定义为 `enum('1','0')`。
-- MySQL 对 ENUM 的 ORDER BY 是按「成员下标」排序，而非字符串值：
--   在 enum('1','0') 中，'1' 的下标是 1，'0' 的下标是 2。
--   因此 `ORDER BY col DESC` 会把 '0'（下标 2）排在 '1'（下标 1）之前——完全反了。
--
-- 直接后果：getDefaultBO()/取默认角色/取默认模板 等使用
--   `ORDER BY isDefault DESC, createTime DESC LIMIT 1`
-- 的查询，在同类存在多条启用记录时，会取到「非默认」那条。
-- 例如配置腾讯云对象存储为默认（isDefault=1）后，仍取到 local（isDefault=0），
-- 导致音频、头像等文件始终落到本地存储；且被 Redis 缓存后清缓存也无效（下次查库仍错）。
--
-- 修复：将这些列翻转为 enum('0','1')。这是「保值」的元数据变更——
-- 已存储的字符串值不变（'1' 仍是 '1'，'0' 仍是 '0'），仅改变成员下标，
-- 使 `ORDER BY col DESC` 正确地把 '1' 排在 '0' 之前。
-- 代码中没有任何地方对这些列做 orderByAsc，故翻转全局安全。
--
-- 覆盖当前库中全部 enum('1','0') 列，逐列保留其原有可空性、默认值与注释。

ALTER TABLE `xiaozhi`.`sys_config`
  MODIFY COLUMN `isDefault` enum('0','1') NULL DEFAULT '0' COMMENT '是否为默认配置: 1-是, 0-否',
  MODIFY COLUMN `state`     enum('0','1') NULL DEFAULT '1' COMMENT '状态：1-启用，0-禁用';

ALTER TABLE `xiaozhi`.`sys_role`
  MODIFY COLUMN `isDefault` enum('0','1') NULL DEFAULT '0' COMMENT '是否默认角色：1-是，0-否',
  MODIFY COLUMN `state`     enum('0','1') NULL DEFAULT '1' COMMENT '状态：1-启用，0-禁用';

ALTER TABLE `xiaozhi`.`sys_template`
  MODIFY COLUMN `isDefault` enum('0','1') NULL DEFAULT '0' COMMENT '是否为默认配置: 1-是, 0-否',
  MODIFY COLUMN `state`     enum('0','1') NULL DEFAULT '1' COMMENT '状态(1启用 0禁用)';

ALTER TABLE `xiaozhi`.`sys_message`
  MODIFY COLUMN `state`     enum('0','1') NULL DEFAULT '1' COMMENT '状态：1-有效，0-删除';

ALTER TABLE `xiaozhi`.`sys_auth_role`
  MODIFY COLUMN `status`    enum('0','1') NULL DEFAULT '1' COMMENT '状态(1正常 0禁用)';

ALTER TABLE `xiaozhi`.`sys_permission`
  MODIFY COLUMN `status`    enum('0','1') NULL DEFAULT '1' COMMENT '状态(1正常 0禁用)',
  MODIFY COLUMN `visible`   enum('0','1') NULL DEFAULT '1' COMMENT '是否可见(1可见 0隐藏)';

ALTER TABLE `xiaozhi`.`sys_user`
  MODIFY COLUMN `isAdmin`     enum('0','1') NULL DEFAULT NULL COMMENT '',
  MODIFY COLUMN `state`       enum('0','1') NULL DEFAULT '1' COMMENT '1-正常 0-禁用',
  MODIFY COLUMN `tokenNotify` enum('0','1') NULL DEFAULT '0' COMMENT '是否启用Token使用量提醒：1-启用，0-禁用';
