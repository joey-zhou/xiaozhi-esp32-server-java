-- 第三方登录授权表。列名与 UserAuthDO 的 @TableField 逐字一致，mybatis 未开启驼峰转换。

CREATE TABLE IF NOT EXISTS `sys_user_auth` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int unsigned NOT NULL COMMENT '用户ID',
  `open_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '第三方平台用户标识',
  `union_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方平台开放平台标识',
  `platform` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '第三方平台，如 wechat',
  `profile` text COLLATE utf8mb4_unicode_ci COMMENT '第三方返回的原始资料 JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_open_id` (`platform`, `open_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方授权表';
