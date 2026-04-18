-- 为 sys_message 加 metadata JSON 列，承载 UserMessage 的附加元数据
-- （speaker/emotion/emotionScore/emotionDegree 等结构化字段）
-- 这些字段原本作为文本前缀 [说话人:X][neutral] 拼接到 message 列中，
-- 现改为独立结构化存储，message 列仅保留用户裸文本。
-- 读出送 LLM 时由 Conversation 层做运行时投影恢复前缀。

ALTER TABLE sys_message
    ADD COLUMN metadata JSON NULL COMMENT 'UserMessage 附加元数据(speaker/emotion 等)，JSON 格式' AFTER message;
