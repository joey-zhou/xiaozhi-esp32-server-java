package com.xiaozhi.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ListenMode {
    AUTO("auto"),
    MANUAL("manual"),
    REAL_TIME("realtime");

    @JsonValue
    private final String value;

    ListenMode(String value) {
        this.value = value;
    }
}
