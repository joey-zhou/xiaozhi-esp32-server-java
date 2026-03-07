package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dto.param.AgentAddParam;
import com.xiaozhi.dto.param.AgentUpdateParam;
import com.xiaozhi.dto.response.AgentDTO;
import com.xiaozhi.entity.SysAgent;
import com.xiaozhi.service.SysAgentService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询智能体", description = "返回智能体列表信息，会自动查询Coze和Dify当前存在智能体并更新本地数据库信息")
    public ResultMessage list(SysAgent agent) {
        try {
            List<SysAgent> sysAgents = agentService.query(agent);

            // 转换为DTO
            List<AgentDTO> agentDTOList = DtoConverter.toAgentDTOList(sysAgents);

            Map<String, Object> data = new HashMap<>();
            data.put("list", agentDTOList);
            data.put("total", agentDTOList.size());
            return ResultMessage.success(data);
        }catch (Exception e){
            logger.error("查询智能体列表失败", e);
            return ResultMessage.error(e.getMessage());
        }
    }

    /**
     * 添加智能体
     *
     * @param param 智能体信息
     * @return 添加结果
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "添加智能体", description = "添加新的Coze或Dify智能体配置")
    public ResultMessage create(@Valid @RequestBody AgentAddParam param) {
        try {
            SysAgent agent = new SysAgent();
            BeanUtils.copyProperties(param, agent);
            agent.setUserId(CmsUtils.getUserId());

            agentService.add(agent);

            // 返回新增的智能体信息(不包含敏感字段)
            return ResultMessage.success(DtoConverter.toAgentDTO(agent));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("添加智能体失败");
        }
    }

    /**
     * 更新智能体
     *
     * @param agentId 智能体ID
     * @param param 智能体信息
     * @return 更新结果
     */
    @PutMapping("/{agentId}")
    @ResponseBody
    @Operation(summary = "更新智能体", description = "更新智能体配置信息")
    public ResultMessage update(@PathVariable Integer agentId, @Valid @RequestBody AgentUpdateParam param) {
        try {
            SysAgent agent = new SysAgent();
            BeanUtils.copyProperties(param, agent);
            agent.setAgentId(agentId);

            agentService.update(agent);

            // 返回更新成功消息（AgentService没有selectById方法）
            return ResultMessage.success("更新成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("更新智能体失败");
        }
    }

    /**
     * 删除智能体
     *
     * @param agentId 智能体ID
     * @return 删除结果
     */
    @DeleteMapping("/{agentId}")
    @ResponseBody
    @Operation(summary = "删除智能体", description = "删除指定的智能体配置")
    public ResultMessage delete(@PathVariable Integer agentId) {
        try {
            agentService.delete(agentId);
            return ResultMessage.success("删除成功");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error("删除智能体失败");
        }
    }
}