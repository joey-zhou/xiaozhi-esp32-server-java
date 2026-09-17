package com.xiaozhi.config.convert;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.req.ConfigCreateReq;
import com.xiaozhi.common.model.req.ConfigTestReq;
import com.xiaozhi.common.model.req.ConfigUpdateReq;
import com.xiaozhi.common.model.resp.ConfigResp;
import com.xiaozhi.config.dal.mysql.dataobject.ConfigDO;
import com.xiaozhi.config.domain.AiConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface ConfigConvert {

    ConfigBO toBO(ConfigDO configDO);

    @Mapping(target = "configId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "apiKey", source = "apiKey", qualifiedByName = "blankToNull")
    @Mapping(target = "apiSecret", source = "apiSecret", qualifiedByName = "blankToNull")
    @Mapping(target = "ak", source = "ak", qualifiedByName = "blankToNull")
    @Mapping(target = "sk", source = "sk", qualifiedByName = "blankToNull")
    ConfigBO toBO(ConfigCreateReq req);

    @Mapping(target = "configId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "configType", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "apiKey", source = "apiKey", qualifiedByName = "blankToNull")
    @Mapping(target = "apiSecret", source = "apiSecret", qualifiedByName = "blankToNull")
    @Mapping(target = "ak", source = "ak", qualifiedByName = "blankToNull")
    @Mapping(target = "sk", source = "sk", qualifiedByName = "blankToNull")
    ConfigBO toBO(ConfigUpdateReq req);

    ConfigResp toResp(ConfigBO configBO);

    /**
     * 写路径出参：字段全部取自刚落库的聚合根，时间戳由 Repository 在写完后回填。
     * <p>密钥字段不在 ConfigResp 里，不会随出参外泄。
     */
    @Mapping(target = "isDefault", expression = "java(config.isDefault() ? \"1\" : \"0\")")
    ConfigResp toResp(AiConfig config);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "apiKey", source = "apiKey", qualifiedByName = "blankToNull")
    @Mapping(target = "apiSecret", source = "apiSecret", qualifiedByName = "blankToNull")
    @Mapping(target = "ak", source = "ak", qualifiedByName = "blankToNull")
    @Mapping(target = "sk", source = "sk", qualifiedByName = "blankToNull")
    ConfigBO toBO(ConfigTestReq req);

    /** 密钥字段的空白串统一规范成 null，使「没填」在全仓只有 null 一种表示。 */
    @Named("blankToNull")
    static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
