INSERT INTO `xiaozhi`.`sys_config` (`userId`, `configType`, `provider`, `configName`, `configDesc`, `isDefault`, `state`)
SELECT
    COALESCE(
        (SELECT u1.`userId` FROM `xiaozhi`.`sys_user` u1 WHERE u1.`username` = 'admin' LIMIT 1),
        (SELECT MIN(u2.`userId`) FROM `xiaozhi`.`sys_user` u2)
    ),
    'oss',
    'local',
    '默认本地存储',
    '未配置云存储时默认使用本地存储',
    '1',
    '1'
FROM DUAL
WHERE EXISTS (
    SELECT 1
    FROM `xiaozhi`.`sys_user` u
)
AND NOT EXISTS (
    SELECT 1
    FROM `xiaozhi`.`sys_config` c
    WHERE c.`configType` = 'oss'
      AND c.`state` = '1'
);
