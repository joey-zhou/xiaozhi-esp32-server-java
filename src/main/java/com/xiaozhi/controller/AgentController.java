package com.xiaozhi.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.entity.SysAgent;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysAgentService;
import com.xiaozhi.utils.CmsUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 智能体管理
 * 
 * @author Joey
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    @Resource
    private SysAgentService agentService;

    /**
     * 查询智能体列表
     * 
     * @param agent    查询条件
     * @param exchange 请求交换
     * @return 智能体列表
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(SysAgent agent, ServerWebExchange exchange) {
        // 从请求属性中获取用户信息
        SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
        if (user != null) {
            agent.setUserId(user.getUserId());
        }
        
        return agentService.query(agent)
                .map(list -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("list", list);
                    data.put("total", list.size());
                    return AjaxResult.success(data);
                })
                .onErrorResume(e -> {
                    logger.error("查询智能体列表失败", e);
                    return Mono.just(AjaxResult.error("查询智能体列表失败"));
                });
    }

    /**
     * 添加智能体
     * 
     * @param agent    智能体信息
     * @param exchange 请求交换
     * @return 添加结果
     */
    @PostMapping("/add")
    public Mono<AjaxResult> add(@RequestBody SysAgent agent, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            try {
                // 从请求属性中获取用户信息
                SysUser user = exchange.getAttribute(CmsUtils.USER_ATTRIBUTE_KEY);
                if (user != null) {
                    agent.setUserId(user.getUserId());
                }

                agentService.add(agent);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error("添加智能体失败");
            }
        });
    }

    /**
     * 更新智能体
     * 
     * @param agent 智能体信息
     * @return 更新结果
     */
    @PostMapping("/update")
    public Mono<AjaxResult> update(@RequestBody SysAgent agent) {
        return Mono.fromCallable(() -> {
            try {
                agentService.update(agent);
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error("更新智能体失败");
            }
        });
    }

    /**
     * 删除智能体
     * 
     * @param agent 智能体信息
     * @return 删除结果
     */
    @PostMapping("/delete")
    public Mono<AjaxResult> delete(@RequestBody SysAgent agent) {
        return Mono.fromCallable(() -> {
            try {
                agentService.delete(agent.getAgentId());
                return AjaxResult.success();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return AjaxResult.error("删除智能体失败");
            }
        });
    }
}