package com.xiaozhi.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ListenMode {
    Auto("auto"),
    Manual("manual"),
    RealTime("realtime");

    @JsonValue
    private final String value;

    ListenMode(String value) {
        this.value = value;
    }
}
