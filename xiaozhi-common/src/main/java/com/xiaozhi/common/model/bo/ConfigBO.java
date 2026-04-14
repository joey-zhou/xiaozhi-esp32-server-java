package com.xiaozhi.common.model.bo;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ConfigBO {

    public static final String STATE_ENABLED = "1";
    public static final String STATE_DISABLED = "0";
    public static final String DEFAULT_YES = "1";
    public static final String DEFAULT_NO = "0";

    @Getter
    public enum ModelType {
        chat("chat"),
        vision("vision"),
        intent("intent"),
        embedding("embedding");

        @JsonValue
        private final String value;

        ModelType(String value) {
            this.value = value;
        }
    }

    private Integer configId;
    private Integer userId;
    private String configName;
    private String configDesc;
    private String configType;
    private String modelType;
    private String provider;
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String ak;
    private String sk;
    private String apiUrl;
    private String state;
    private String isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
