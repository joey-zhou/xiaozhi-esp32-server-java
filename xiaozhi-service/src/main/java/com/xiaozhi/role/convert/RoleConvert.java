package com.xiaozhi.role.convert;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.resp.RoleResp;
import com.xiaozhi.role.dal.mysql.dataobject.RoleDO;
import com.xiaozhi.role.domain.Role;
import com.xiaozhi.role.model.RoleProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleConvert {

    RoleResp toResp(RoleProjection projection);

    /**
     * 读路径的 DO → BO：该列为 NULL 的历史行补默认值。
     * <p>默认值本身由领域模型持有，这里只引用，不再写第二份字面量——写路径经聚合根补的是同一份，
     * 两边各存一个数字迟早会对不上。
     */
    @Mapping(target = "ttsPitch", source = "ttsPitch",
        defaultExpression = "java(com.xiaozhi.role.domain.vo.VoiceConfig.DEFAULT_TTS_PITCH)")
    @Mapping(target = "ttsSpeed", source = "ttsSpeed",
        defaultExpression = "java(com.xiaozhi.role.domain.vo.VoiceConfig.DEFAULT_TTS_SPEED)")
    @Mapping(target = "temperature", source = "temperature",
        defaultExpression = "java(com.xiaozhi.role.domain.vo.LlmConfig.DEFAULT_TEMPERATURE)")
    @Mapping(target = "topP", source = "topP",
        defaultExpression = "java(com.xiaozhi.role.domain.vo.LlmConfig.DEFAULT_TOP_P)")
    @Mapping(target = "inactiveTimeoutSeconds", source = "inactiveTimeoutSeconds",
        defaultExpression = "java(Integer.valueOf(com.xiaozhi.role.domain.Role.DEFAULT_INACTIVE_TIMEOUT_SECONDS))")
    RoleBO toBO(RoleDO roleDO);

    /**
     * 写路径出参：字段全部取自刚落库的聚合根，默认值由聚合根在构建时补齐。
     * <p>modelName / modelProvider / ttsProvider / totalDevice 是分页 SQL 才 JOIN 出来的展示列，
     * createTime / updateTime 同样不在这两个接口的返回里，保持与改造前一致留空。
     */
    @Mapping(target = "isDefault", expression = "java(role.isDefault() ? \"1\" : \"0\")")
    @Mapping(target = "modelId", source = "llmConfig.modelId")
    @Mapping(target = "temperature", source = "llmConfig.temperature")
    @Mapping(target = "topP", source = "llmConfig.topP")
    @Mapping(target = "ttsId", source = "voiceConfig.ttsId")
    @Mapping(target = "sttId", source = "voiceConfig.sttId")
    @Mapping(target = "sttHotwords", source = "voiceConfig.sttHotwords")
    @Mapping(target = "voiceName", source = "voiceConfig.voiceName")
    @Mapping(target = "ttsPitch", source = "voiceConfig.ttsPitch")
    @Mapping(target = "ttsSpeed", source = "voiceConfig.ttsSpeed")
    @Mapping(target = "vadEnergyTh", source = "audioConfig.vadEnergyTh")
    @Mapping(target = "vadSpeechTh", source = "audioConfig.vadSpeechTh")
    @Mapping(target = "vadSilenceTh", source = "audioConfig.vadSilenceTh")
    @Mapping(target = "vadSilenceMs", source = "audioConfig.vadSilenceMs")
    @Mapping(target = "modelName", ignore = true)
    @Mapping(target = "modelProvider", ignore = true)
    @Mapping(target = "ttsProvider", ignore = true)
    @Mapping(target = "totalDevice", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleResp toResp(Role role);

    @Mapping(target = "roleId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RoleDO copy(RoleDO roleDO);
}
