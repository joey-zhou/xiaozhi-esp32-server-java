-- 「同类唯一默认」交给数据库保证。
-- 虚拟生成列把「是否受约束 + 作用域」编码成可为 NULL 的键，非默认行 key 为 NULL 天然可重复，
-- 再在该列上建唯一索引；isDefault 的类型与取值不变。
-- 三个键表达式不能统一，差异见每段注释。
-- 去重必须在建索引之前，保留 createTime 最新的一条（与读侧取默认的顺序一致）。

-- ---------------------------------------------------------------------------
-- sys_config：作用域 = configType，只有 llm 再按 modelType 细分。
-- 键不带 userId：配置是全局资源，读取端 getDefaultBO 也全局取默认。
-- 键带 state：软删的配置不参与默认。
-- 条件须与 ConfigRepositoryImpl.resetDefault 逐条一致，不一致时写入路径才会炸。
-- ---------------------------------------------------------------------------
UPDATE `sys_config` c
JOIN (
    SELECT `configId` FROM (
        SELECT `configId`,
               ROW_NUMBER() OVER (
                   PARTITION BY `configType`,
                                IF(`configType` = 'llm', IFNULL(`modelType`, ''), '')
                   ORDER BY `createTime` DESC, `configId` DESC
               ) AS rn
        FROM `sys_config`
        WHERE `isDefault` = '1' AND `state` = '1'
    ) ranked WHERE ranked.rn > 1
) dup ON c.`configId` = dup.`configId`
SET c.`isDefault` = '0';

SET @exists_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'defaultKey');
SET @sql = IF(@exists_col = 0,
    'ALTER TABLE `sys_config` ADD COLUMN `defaultKey` VARCHAR(80)
        GENERATED ALWAYS AS (IF(`isDefault` = ''1'' AND `state` = ''1'',
            CONCAT(`configType`, '':'',
                   IF(`configType` = ''llm'', IFNULL(`modelType`, ''''), '''')),
            NULL)) VIRTUAL COMMENT ''唯一默认约束键，非默认行为 NULL''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND INDEX_NAME = 'uk_config_default');
SET @sql = IF(@exists_idx = 0,
    'ALTER TABLE `sys_config` ADD UNIQUE KEY `uk_config_default` (`defaultKey`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- sys_role：作用域 = userId。角色是硬删，键不带 state——被禁用的角色仍占着该用户的默认位。
-- ---------------------------------------------------------------------------
UPDATE `sys_role` r
JOIN (
    SELECT `roleId` FROM (
        SELECT `roleId`,
               ROW_NUMBER() OVER (PARTITION BY `userId` ORDER BY `createTime` DESC, `roleId` DESC) AS rn
        FROM `sys_role`
        WHERE `isDefault` = '1'
    ) ranked WHERE ranked.rn > 1
) dup ON r.`roleId` = dup.`roleId`
SET r.`isDefault` = '0';

SET @exists_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'defaultKey');
SET @sql = IF(@exists_col = 0,
    'ALTER TABLE `sys_role` ADD COLUMN `defaultKey` INT UNSIGNED
        GENERATED ALWAYS AS (IF(`isDefault` = ''1'', `userId`, NULL)) VIRTUAL
        COMMENT ''唯一默认约束键，非默认行为 NULL''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND INDEX_NAME = 'uk_role_default');
SET @sql = IF(@exists_idx = 0,
    'ALTER TABLE `sys_role` ADD UNIQUE KEY `uk_role_default` (`defaultKey`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- sys_template：作用域 = userId。模板是软删且不清 isDefault，键必须带 state，
-- 否则一条软删的默认模板会永久占住该用户的键位。
-- ---------------------------------------------------------------------------
UPDATE `sys_template` t
JOIN (
    SELECT `templateId` FROM (
        SELECT `templateId`,
               ROW_NUMBER() OVER (PARTITION BY `userId` ORDER BY `createTime` DESC, `templateId` DESC) AS rn
        FROM `sys_template`
        WHERE `isDefault` = '1' AND `state` = '1'
    ) ranked WHERE ranked.rn > 1
) dup ON t.`templateId` = dup.`templateId`
SET t.`isDefault` = '0';

SET @exists_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_template' AND COLUMN_NAME = 'defaultKey');
SET @sql = IF(@exists_col = 0,
    'ALTER TABLE `sys_template` ADD COLUMN `defaultKey` INT UNSIGNED
        GENERATED ALWAYS AS (IF(`isDefault` = ''1'' AND `state` = ''1'', `userId`, NULL)) VIRTUAL
        COMMENT ''唯一默认约束键，非默认行为 NULL''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_template' AND INDEX_NAME = 'uk_template_default');
SET @sql = IF(@exists_idx = 0,
    'ALTER TABLE `sys_template` ADD UNIQUE KEY `uk_template_default` (`defaultKey`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
