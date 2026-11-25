package com.xiaozhi.common.port;

import com.xiaozhi.common.model.bo.PlatformAgentBO;

import java.util.List;

/**
 * 查询智能体平台上的智能体，ai 实现、service 消费。
 * 平台没答复（网络、HTTP 状态、业务码）一律抛 {@link IllegalStateException}
 */
public interface AgentPlatformClient {

    /** coze 空间里已发布的智能体 */
    List<PlatformAgentBO> listCozeBots(String spaceId, String token);

    /** dify 应用的名称与描述 */
    PlatformAgentBO difyInfo(String apiUrl, String apiKey);

    /** dify 应用的头像，没有返回 null */
    String difyIcon(String apiUrl, String apiKey);
}
