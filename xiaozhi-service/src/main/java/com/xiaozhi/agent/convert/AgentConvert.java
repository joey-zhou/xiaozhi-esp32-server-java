package com.xiaozhi.agent.convert;

import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.resp.AgentResp;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentConvert {

    AgentResp toResp(AgentBO agentBO);
}
