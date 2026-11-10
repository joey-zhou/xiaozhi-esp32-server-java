package com.xiaozhi.agent;

import java.util.Set;

/** 第三方智能体平台的 provider 名，AgentServiceImpl 与 ConfigServiceImpl 共用同一份，避免两处各写一份清单。 */
public final class AgentProviders {

    private AgentProviders() {}

    public static final String COZE = "coze";
    public static final String DIFY = "dify";
    public static final String XINGCHEN = "xingchen";

    public static final Set<String> ALL = Set.of(COZE, DIFY, XINGCHEN);

    /** 平台凭据（appId、apiKey、空间 ID 等）在 sys_config 里的 configType，平台上的智能体落成 llm 配置 */
    public static final String CREDENTIAL_CONFIG_TYPE = "agent";
}
