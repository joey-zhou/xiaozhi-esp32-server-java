package com.xiaozhi.agent.convert;

import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.AgentResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

@Mapper(componentModel = "spring")
public interface AgentConvert {

    AgentResp toResp(AgentBO agentBO);

    /**
     * 第三方平台的智能体在本系统里以一条 llm 配置的形态落库，列表行就由这条配置拼出。
     * <p>
     * botId 与 iconUrl 在 sys_config 里没有对应列，由调用方按平台侧快照补；
     * deviceId / roleId / agentId 是设备与角色场景才用的字段，配置里没有来源。
     */
    @Mapping(target = "agentName", source = "configName")
    @Mapping(target = "agentDesc", source = "configDesc")
    @Mapping(target = "publishTime", source = "createTime")
    @Mapping(target = "botId", ignore = true)
    @Mapping(target = "iconUrl", ignore = true)
    @Mapping(target = "deviceId", ignore = true)
    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "agentId", ignore = true)
    AgentBO toBO(ConfigBO config);

    /** publishTime 对外是 Date，配置里的时间是 LocalDateTime */
    default Date toDate(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
