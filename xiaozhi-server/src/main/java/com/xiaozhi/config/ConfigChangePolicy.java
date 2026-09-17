package com.xiaozhi.config;

import com.xiaozhi.common.exception.ConfirmRequiredException;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.storage.service.StorageReferenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 配置写前的拦截规则：换对象存储前的存量校验要先查库才能判定，
 * 因此从 ConfigAppService 收口到这里，AppService 只管调用与事务边界。
 * <p>
 * {@code check} 开头的方法不满足就抛异常；{@code confirmed} 表示用户已确认切换对象存储，
 * 为 true 时跳过历史文件统计。
 */
@Component
public class ConfigChangePolicy {

    private static final String OSS_CONFIG_TYPE = "oss";

    @Resource
    private StorageReferenceService storageReferenceService;

    public void checkCreate(ConfigCreateReq req, boolean confirmed) {
        if (!confirmed && isOss(req.getConfigType()) && isDefault(req.getIsDefault())) {
            checkNoStoredFiles();
        }
    }

    public void checkUpdate(ConfigBO current, ConfigUpdateReq req, boolean confirmed) {
        if (!confirmed && isOss(current.getConfigType()) && switchesStorage(current, req)) {
            checkNoStoredFiles();
        }
    }

    public void checkDelete(ConfigBO current, boolean confirmed) {
        // 删掉当前默认的那条 oss 配置，等于把存储切回本地，历史云地址一样解析不出来，
        // 与「默认让位」是同一件事，判定口径保持一致
        if (!confirmed && isOss(current.getConfigType()) && isDefault(current.getIsDefault())) {
            checkNoStoredFiles();
        }
    }

    /** 存量统计要扫消息表，只在改动确实会换掉存储时才跑，别让每次改配置都付这笔开销 */
    private void checkNoStoredFiles() {
        long count = storageReferenceService.countOnCurrentStorage();
        if (count > 0) {
            throw new ConfirmRequiredException(
                    "当前对象存储上还有 " + count + " 条历史音频/文件，切换后这些内容将永久无法访问");
        }
    }

    /**
     * 只有默认那条 oss 配置决定当前生效的存储，因此三种改动会让历史地址失效：
     * 非默认改成默认、默认让位（含改回本地）、默认自己换掉地址前缀。
     */
    private static boolean switchesStorage(ConfigBO current, ConfigUpdateReq req) {
        return isDefault(current.getIsDefault())
                ? ConfigBO.DEFAULT_NO.equals(req.getIsDefault()) || prefixChanged(current, req)
                : isDefault(req.getIsDefault());
    }

    /**
     * provider 决定域名形态，configName 是桶名，appId 是地域，apiUrl 是 endpoint；
     * ak/sk 这类凭证不进前缀，改了不影响历史地址能不能解析。
     */
    private static boolean prefixChanged(ConfigBO current, ConfigUpdateReq req) {
        return changed(req.getProvider(), current.getProvider())
                || changed(req.getConfigName(), current.getConfigName())
                || changed(req.getAppId(), current.getAppId())
                || changed(req.getApiUrl(), current.getApiUrl());
    }

    /** null 表示本次不改这个字段，与 {@link com.xiaozhi.config.domain.AiConfig#update} 的字段合并语义一致 */
    private static boolean changed(String patchValue, String currentValue) {
        return patchValue != null && !patchValue.equals(currentValue);
    }

    private static boolean isOss(String configType) {
        return OSS_CONFIG_TYPE.equals(configType);
    }

    private static boolean isDefault(String isDefault) {
        return ConfigBO.DEFAULT_YES.equals(isDefault);
    }
}
