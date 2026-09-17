package com.xiaozhi.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ListenState {
    START("start"),
    STOP("stop"),
    TEXT("text"),
    DETECT("detect");

    @JsonValue
    private final String value;

    ListenState(String value) {
        this.value = value;
    }
}
