package com.xiaozhi.agent.service;

import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;

public interface AgentService {

    PageResp<AgentResp> page(int pageNo, int pageSize, String provider, String agentName, Integer userId);
}
