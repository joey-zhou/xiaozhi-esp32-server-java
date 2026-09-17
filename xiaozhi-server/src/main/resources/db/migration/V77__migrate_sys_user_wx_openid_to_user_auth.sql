-- 早期小程序登录把 openid 写在 sys_user.wxOpenId，后来登录改查 sys_user_auth，V57 只建了空表没搬数据。
-- 先把旧列里的绑定搬进 sys_user_auth，再删掉这两列；已存在的 (platform, open_id) 由唯一键跳过。

INSERT IGNORE INTO `sys_user_auth` (`user_id`, `open_id`, `union_id`, `platform`)
SELECT `userId`, `wxOpenId`, NULLIF(`wxUnionId`, ''), 'wechat'
FROM `sys_user`
WHERE `wxOpenId` IS NOT NULL AND `wxOpenId` <> '';

ALTER TABLE `sys_user`
  DROP COLUMN `wxOpenId`,
  DROP COLUMN `wxUnionId`;
