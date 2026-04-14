package com.xiaozhi.agent;

import com.xiaozhi.agent.service.AgentService;
import com.xiaozhi.common.model.req.AgentPageReq;
import com.xiaozhi.common.model.resp.AgentResp;
import com.xiaozhi.common.model.resp.PageResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Agent 领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>跨领域校验</li>
 * </ul>
 */
@Service
public class AgentAppService {

    @Resource
    private AgentService agentService;

    public PageResp<AgentResp> page(AgentPageReq req, Integer userId) {
        AgentPageReq r = req == null ? new AgentPageReq() : req;
        return agentService.page(r.getPageNo(), r.getPageSize(), r.getProvider(), r.getAgentName(), userId);
    }
}
