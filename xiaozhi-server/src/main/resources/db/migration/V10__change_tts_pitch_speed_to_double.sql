-- 将 TTS 语速/音调字段从 FLOAT 调整为 DOUBLE，与 Java 侧 Double 保持一致，避免隐式精度降级。

ALTER TABLE `sys_role`
    MODIFY COLUMN `ttsPitch` DOUBLE DEFAULT 1.0 COMMENT '语音音调',
    MODIFY COLUMN `ttsSpeed` DOUBLE DEFAULT 1.0 COMMENT '语音语速';
