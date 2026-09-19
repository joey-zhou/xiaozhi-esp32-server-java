-- 语音识别热词：每行一个「词」或「词 权重」，权重不填按默认。
-- 仅部分 provider 支持（腾讯、火山、FunASR），不支持的按无热词识别，切换 provider 时本列不清空。
ALTER TABLE `sys_role`
  ADD COLUMN `sttHotwords` text NULL COMMENT '语音识别热词，每行一个「词 [权重]」' AFTER `sttId`;
