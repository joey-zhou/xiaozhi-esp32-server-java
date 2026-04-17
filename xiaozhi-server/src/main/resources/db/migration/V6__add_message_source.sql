-- 新增消息来源字段，区分 Web 聊天 / 设备对话 / 未来其他来源
-- 存量数据（迁移前全部为设备对话）由 DEFAULT 'device' 自动处理
ALTER TABLE `sys_message`
    ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'device'
    COMMENT '消息来源: web|device|...（存量默认 device）'
    AFTER `sessionId`;
