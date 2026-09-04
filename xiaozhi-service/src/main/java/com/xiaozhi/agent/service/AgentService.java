package com.xiaozhi.agent.service;

import com.xiaozhi.common.model.bo.AgentBO;
import com.xiaozhi.common.model.PageResult;

public interface AgentService {

    PageResult<AgentBO> page(int pageNo, int pageSize, String provider, String agentName, Integer userId);
}
