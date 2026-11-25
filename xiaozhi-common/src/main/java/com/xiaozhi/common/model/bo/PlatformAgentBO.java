package com.xiaozhi.common.model.bo;

import java.time.LocalDateTime;

/**
 * 智能体平台上的一个智能体
 *
 * @param id          平台上的标识（coze 的 botId），dify 没有
 * @param publishTime 平台给出的发布时间，没有为 null
 */
public record PlatformAgentBO(String id, String name, String description, String iconUrl, LocalDateTime publishTime) {
}
