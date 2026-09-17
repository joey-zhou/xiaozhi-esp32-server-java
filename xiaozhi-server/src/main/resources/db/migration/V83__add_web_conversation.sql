-- Web 聊天会话表：侧栏的会话列表、标题与归属都读这张表，不再从消息表按 sessionId 聚合。
-- updateTime 是最后一次对话的时间，由应用在每轮落库时写入；改标题不刷新它，所以不挂 ON UPDATE。

CREATE TABLE IF NOT EXISTS `sys_conversation` (
  `sessionId` varchar(64) NOT NULL COMMENT '会话ID',
  `userId` int unsigned NOT NULL COMMENT '所属用户ID',
  `roleId` int unsigned NOT NULL COMMENT '角色ID',
  `title` varchar(255) NULL COMMENT '会话标题',
  `createTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后对话时间',
  PRIMARY KEY (`sessionId`),
  KEY `idx_conversation_user_update` (`userId`, `updateTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Web 聊天会话表';

-- 已有的 Web 会话从消息表补进来：标题取第一句用户消息的开头，最后对话时间取最后一条消息
INSERT IGNORE INTO `sys_conversation` (`sessionId`, `userId`, `roleId`, `title`, `createTime`, `updateTime`)
SELECT m.`sessionId`,
       MIN(m.`userId`),
       MIN(m.`roleId`),
       LEFT((SELECT sm.`message` FROM `sys_message` sm
             WHERE sm.`sessionId` = m.`sessionId` AND sm.`sender` = 'user' AND sm.`state` = '1'
             ORDER BY sm.`createTime`, sm.`messageId`
             LIMIT 1), 50),
       MIN(m.`createTime`),
       MAX(m.`createTime`)
FROM `sys_message` m
WHERE m.`source` = 'web'
  AND m.`state` = '1'
  AND m.`sessionId` IS NOT NULL AND m.`sessionId` <> ''
  AND m.`userId` IS NOT NULL
  AND m.`roleId` IS NOT NULL
GROUP BY m.`sessionId`;
