package com.xiaozhi.agent;

import com.xiaozhi.server.web.BaseController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.agent.AgentAppService;
import com.xiaozhi.common.model.req.AgentPageReq;
import com.xiaozhi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体管理
 * 
 * @author Joey
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "智能体管理", description = "Coze、Dify智能体相关操作")
public class AgentController extends BaseController {

    @Resource
    private AgentAppService agentAppService;

    /**
     * 查询智能体列表
     *
     * @param req 查询条件
     * @return 智能体列表
     */
    @GetMapping("")
    @ResponseBody
    @SaCheckPermission("system:config:agent:api:list")
    @Operation(summary = "根据条件查询智能体", description = "返回智能体列表信息，会自动查询平台当前存在的智能体并同步本地配置")
    public ApiResponse<?> list(@Valid AgentPageReq req) {
        return ApiResponse.success(agentAppService.page(req, StpUtil.getLoginIdAsInt()));
    }
}
