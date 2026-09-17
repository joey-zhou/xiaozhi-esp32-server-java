-- 为已有用户补建网页聊天虚拟设备 user_chat_<userId>。
-- roleId 取该用户的默认角色，没有默认角色则取任意一个；都没有时留 NULL，由未绑定设备流程接管。

INSERT INTO `xiaozhi`.`sys_device` (`deviceId`, `deviceName`, `roleId`, `type`, `state`, `userId`)
SELECT
    CONCAT('user_chat_', u.`userId`),
    '网页聊天',
    COALESCE(
        (SELECT r.`roleId` FROM `xiaozhi`.`sys_role` r
          WHERE r.`userId` = u.`userId` AND r.`isDefault` = '1'
          ORDER BY r.`roleId` LIMIT 1),
        (SELECT r.`roleId` FROM `xiaozhi`.`sys_role` r
          WHERE r.`userId` = u.`userId`
          ORDER BY r.`roleId` LIMIT 1)
    ),
    'web',
    '0',
    u.`userId`
FROM `xiaozhi`.`sys_user` u
LEFT JOIN `xiaozhi`.`sys_device` d ON d.`deviceId` = CONCAT('user_chat_', u.`userId`)
WHERE d.`deviceId` IS NULL;
