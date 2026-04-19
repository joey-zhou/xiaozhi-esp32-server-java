-- sys_config 增加 enableThinking 字段，用于控制模型是否启用思考模式
ALTER TABLE `sys_config`
    ADD COLUMN `enableThinking` TINYINT(1) DEFAULT NULL
    COMMENT '是否启用思考模式'
    AFTER `isDefault`;
