-- 新增设备 MCP 工具列表字段，用于保存设备连接时上报的 MCP tools 名称（逗号分隔）
-- 对应 DeviceDO.mcpList / DeviceMcpService.persistMcpList 的持久化
ALTER TABLE `sys_device`
    ADD COLUMN `mcpList` TEXT NULL
    COMMENT '设备 MCP 工具列表，逗号分隔的工具名称'
    AFTER `deviceName`;
