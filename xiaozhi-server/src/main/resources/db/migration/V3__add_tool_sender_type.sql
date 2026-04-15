-- 为 sys_message 表的 sender 字段添加 'tool' 类型，用于存储工具调用响应消息
ALTER TABLE sys_message
  MODIFY COLUMN sender enum('user','assistant','tool') NOT NULL
  COMMENT '消息发送方：user-用户，assistant-人工智能，tool-工具响应';
