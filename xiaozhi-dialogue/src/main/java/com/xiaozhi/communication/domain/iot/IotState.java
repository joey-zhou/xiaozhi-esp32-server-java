package com.xiaozhi.communication.domain.iot;

import lombok.Data;

import java.util.Map;

@Data
public class IotState {
    private String name;
    private Map<String, Object> state;
}
