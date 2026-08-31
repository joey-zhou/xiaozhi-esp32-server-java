-- V12: 加宽承载「存储路径 / 文件 URL」的列，容纳云存储的完整 URL
--
-- 背景：启用云存储（COS/OSS）后，文件持久化路径从本地相对路径（如
--   audio/2026-07-04/.../xxx-user.wav，约 60 字符）变为完整 URL（如
--   https://your-bucket.cos.ap-beijing.myqcloud.com/audio/.../xxx-user.wav，约 115+ 字符）。
-- 原列宽（sys_message.audioPath varchar(100)、sys_user.avatar varchar(100) 等）不足以容纳，
-- 导致对话持久化时报 "Data too long for column 'audioPath'"，整条消息写入失败。
--
-- 统一加宽到 512：裸 URL 一般在 120 字符左右，深层路径留足余量。
-- 注意：入库前已剥离预签名 query（stripSignature），故存的是裸 URL，不含数百字符的签名串。

ALTER TABLE `xiaozhi`.`sys_message`
  MODIFY COLUMN `audioPath` varchar(512) NULL DEFAULT NULL COMMENT '音频文件路径（本地相对路径或云存储 URL）';

ALTER TABLE `xiaozhi`.`sys_user`
  MODIFY COLUMN `avatar` varchar(512) NULL DEFAULT NULL COMMENT '头像（本地相对路径或云存储 URL）';

ALTER TABLE `xiaozhi`.`sys_role`
  MODIFY COLUMN `avatar` varchar(512) NULL DEFAULT NULL COMMENT '头像（本地相对路径或云存储 URL）';
