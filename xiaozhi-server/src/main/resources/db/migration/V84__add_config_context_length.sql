-- 模型配置记录上下文长度：对话按它的一定比例做 token 预算触发压缩。前端选模型时从模型清单自动带出，也可手改；
-- 为空按应用配置的默认上下文长度处理。
ALTER TABLE `sys_config`
  ADD COLUMN `contextLength` int unsigned NULL COMMENT '模型上下文长度(token)' AFTER `enableThinking`;
