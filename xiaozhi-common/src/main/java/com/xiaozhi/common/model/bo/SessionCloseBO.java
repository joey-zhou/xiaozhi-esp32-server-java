package com.xiaozhi.common.model.bo;

/**
 * 跨实例关闭设备会话的载荷。
 *
 * @param excludeSessionId 新连接自己的会话号：新连接建立过程中会收到自己发出的这条广播，命中它的不关
 */
public record SessionCloseBO(String deviceId, String excludeSessionId) {
}
