package com.xiaozhi.controller;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysAgent;
import com.xiaozhi.service.SysAgentService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

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
    private SysAgentService agentService;

    /**
     * 查询智能体列表
     * 
     * @param agent 查询条件
     * @return 智能体列表
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询智能体", description = "返回智能体列表信息，会自动查询Coze和Dify当前存在智能体并更新本地数据库信息")
    public ResultMessage query(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "智能体信息",
            content = @Content(schema = @Schema(implementation = SysAgent.class))) SysAgent agent) {
        try {
            List<SysAgent> sysAgents = agentService.query(agent);
            Map<String, Object> data = new HashMap<>();
            data.put("list", sysAgents);
            data.put("total", sysAgents.size());
            return ResultMessage.success(data);
        }catch (Exception e){
            logger.error("查询智能体列表失败", e.getMessage());
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 添加智能体
     * 
     * @param agent    智能体信息
     * @return 添加结果
     */
    @PostMapping("/add")
    @ResponseBody
    @Operation(summary = "添加智能体", description = "返回添加结果")
    public ResultMessage add(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "智能体信息",
            content = @Content(schema = @Schema(implementation = SysAgent.class)))
        @RequestBody SysAgent agent) {
        try {

            agent.setUserId(CmsUtils.getUserId());
            agentService.add(agent);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("添加智能体失败");
        }
    }

    /**
     * 更新智能体
     * 
     * @param agent 智能体信息
     * @return 更新结果
     */
    @PostMapping("/update")
    @ResponseBody
    @Operation(summary = "更新智能体", description = "返回更新结果")
    public ResultMessage update(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "智能体信息",
            content = @Content(schema = @Schema(implementation = SysAgent.class)))
        @RequestBody SysAgent agent) {
        try {
            agentService.update(agent);
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("更新智能体失败");
        }
    }

    /**
     * 删除智能体
     * 
     * @param agent 智能体信息
     * @return 删除结果
     */
    @PostMapping("/delete")
    @ResponseBody
    @Operation(summary = "删除智能体", description = "返回删除结果")
    public ResultMessage delete(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "智能体信息",
            content = @Content(schema = @Schema(implementation = SysAgent.class)))
        @RequestBody SysAgent agent) {
        try {
            agentService.delete(agent.getAgentId());
            return ResultMessage.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("删除智能体失败");
        }
    }
}