package com.xiaozhi.config.support;

import org.springframework.util.StringUtils;

/** SYS_CONFIG 缓存里「默认配置」条目的 key 拼装规则，读写两侧共用。 */
public final class ConfigCacheKeys {

    private ConfigCacheKeys() {}

    public static String defaultKey(String configType, String modelType) {
        return StringUtils.hasText(modelType) ? "default:" + configType + ":" + modelType : "default:" + configType;
    }

    /** 查过库、确认没有默认配置时写入的标记，与对应的默认配置 key 一起淘汰 */
    public static String absentKey(String defaultKey) {
        return "absent:" + defaultKey;
    }
}
