package com.xiaozhi.agent;

import java.util.Set;

/** 第三方智能体平台的 provider 名，AgentServiceImpl 与 ConfigServiceImpl 共用同一份，避免两处各写一份清单。 */
public final class AgentProviders {

    private AgentProviders() {}

    public static final String COZE = "coze";
    public static final String DIFY = "dify";
    public static final String XINGCHEN = "xingchen";

    public static final Set<String> ALL = Set.of(COZE, DIFY, XINGCHEN);
}
