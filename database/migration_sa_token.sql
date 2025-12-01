-- ============================================================
-- Sa-Token 认证系统重构 - 数据库迁移脚本
-- 创建时间: 2025-12-01
-- 说明: 创建sys_user_auth表并迁移现有微信数据
-- ============================================================

-- ============================================================
-- 步骤1: 创建用户第三方认证表
-- 功能: 支持一个用户绑定多个第三方平台(微信/QQ/支付宝等)
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user_auth` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` INT NOT NULL COMMENT '用户ID,关联sys_user.user_id',
  `open_id` VARCHAR(100) NOT NULL COMMENT '第三方平台的唯一标识(如微信openid)',
  `union_id` VARCHAR(100) DEFAULT NULL COMMENT '微信unionid(用于同一主体的不同应用)',
  `platform` VARCHAR(20) NOT NULL COMMENT '平台标识: wechat/qq/alipay/apple等',
  `profile` TEXT DEFAULT NULL COMMENT '第三方返回的原始JSON数据',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_open_id` (`open_id`),
  INDEX `idx_user_platform` (`user_id`, `platform`),
  UNIQUE KEY `uk_platform_openid` (`platform`, `open_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户第三方认证信息表';

-- ============================================================
-- 步骤2: 迁移现有微信数据到新表
-- ============================================================
INSERT INTO sys_user_auth (user_id, open_id, union_id, platform, profile)
SELECT
    user_id,
    wx_open_id AS open_id,
    wx_union_id AS union_id,
    'wechat' AS platform,
    JSON_OBJECT(
        'openid', wx_open_id,
        'unionid', wx_union_id,
        'migrated', true,
        'migrate_time', NOW()
    ) AS profile
FROM sys_user
WHERE wx_open_id IS NOT NULL AND wx_open_id != '';

-- ============================================================
-- 步骤3: 验证数据迁移
-- ============================================================
SELECT
    '原始微信数据数量' AS description,
    COUNT(*) AS count
FROM sys_user
WHERE wx_open_id IS NOT NULL AND wx_open_id != ''
UNION ALL
SELECT
    '迁移后数据数量' AS description,
    COUNT(*) AS count
FROM sys_user_auth
WHERE platform = 'wechat';

-- ============================================================
-- 注意事项:
-- 1. 本脚本会保留sys_user表的wx_open_id和wx_union_id字段
-- 2. 稳定运行1-2周后,可以选择删除这些字段
-- 3. 删除前请先备份数据库
-- 4. 删除命令(暂不执行):
--    ALTER TABLE sys_user DROP COLUMN wx_open_id;
--    ALTER TABLE sys_user DROP COLUMN wx_union_id;
-- ============================================================
