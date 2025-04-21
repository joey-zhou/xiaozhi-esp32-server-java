package com.xiaozhi.service;

import java.util.List;

import com.xiaozhi.entity.SysAgent;

import reactor.core.publisher.Mono;

/**
 * 智能体服务接口
 * 
 * @author Joey
 */
public interface SysAgentService {

    /**
     * 添加智能体
     * 
     * @param agent 智能体信息
     * @return 结果
     */
    int add(SysAgent agent);

    /**
     * 修改智能体
     * 
     * @param agent 智能体信息
     * @return 结果
     */
    int update(SysAgent agent);

    /**
     * 删除智能体
     * 
     * @param agentId 智能体ID
     * @return 结果
     */
    int delete(Integer agentId);

    /**
     * 查询智能体列表
     * 
     * @param agent 智能体信息
     * @return 智能体集合的Mono对象
     */
    Mono<List<SysAgent>> query(SysAgent agent);

}