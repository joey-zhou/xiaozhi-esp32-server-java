package com.xiaozhi.agent.service;

import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.PageResult;

public interface AgentService {

    PageResult<AgentResp> page(int pageNo, int pageSize, String provider, String agentName, Integer userId);
}
