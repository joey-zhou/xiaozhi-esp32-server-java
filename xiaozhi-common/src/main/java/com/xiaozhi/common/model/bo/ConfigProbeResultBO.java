package com.xiaozhi.common.model.bo;

/** 配置试拨的结论：成功与否 + 给前端看的一句话说明 */
public record ConfigProbeResultBO(boolean success, String message) {

    public static ConfigProbeResultBO success(String message) {
        return new ConfigProbeResultBO(true, message);
    }

    public static ConfigProbeResultBO failure(String message) {
        return new ConfigProbeResultBO(false, message);
    }
}
