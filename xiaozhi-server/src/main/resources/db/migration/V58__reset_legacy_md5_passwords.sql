-- 口令散列改为 BCrypt，库里残留的 MD5 口令一次性覆盖掉。

-- admin 重置为 BCrypt 后的出厂口令 123456，部署后应立即修改。
UPDATE `sys_user`
SET `password` = '$2a$10$Kszen1V6r4y3z3CODLVu.ORMy7xGt0W7Br1tnt8FsVepDO/t13i4W'
WHERE `username` = 'admin' AND `password` REGEXP '^[0-9a-fA-F]{32}$';

-- 其余账号置为 '!RESET_REQUIRED'（不是合法 BCrypt 串，任何输入都验不过），走邮箱/短信验证码找回重设。
UPDATE `sys_user`
SET `password` = '!RESET_REQUIRED'
WHERE `password` REGEXP '^[0-9a-fA-F]{32}$';
