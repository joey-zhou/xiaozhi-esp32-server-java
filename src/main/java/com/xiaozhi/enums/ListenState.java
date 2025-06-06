package com.xiaozhi.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ListenState {
    Start("start"),
    Stop("stop"),
    Text("text"),
    Detect("detect");

    @JsonValue
    private final String value;

    ListenState(String value) {
        this.value = value;
    }
}
