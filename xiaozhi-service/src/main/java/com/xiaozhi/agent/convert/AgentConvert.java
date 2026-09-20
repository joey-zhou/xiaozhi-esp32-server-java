package com.xiaozhi.agent.convert;

import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.resp.AgentResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface AgentConvert {

    AgentResp toResp(AgentBO agentBO);

    /**
     * 第三方平台的智能体在本系统里以一条 llm 配置的形态落库，列表行就由这条配置拼出。
     * <p>
     * botId 与 iconUrl 在 sys_config 里没有对应列，由调用方按平台侧快照补。
     */
    @Mapping(target = "agentName", source = "configName")
    @Mapping(target = "agentDesc", source = "configDesc")
    @Mapping(target = "publishTime", source = "createTime")
    @Mapping(target = "botId", ignore = true)
    @Mapping(target = "iconUrl", ignore = true)
    AgentBO toBO(ConfigBO config);
}
