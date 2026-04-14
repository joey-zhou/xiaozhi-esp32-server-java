package com.xiaozhi.communication.domain.iot;

import com.xiaozhi.utils.JsonUtil;
import lombok.Data;

/**
 * function_call的参数定义
 */
@Data
public class IotProperty {
    /**
     * 参数描述
     */
    private String description;
    /**
     * 参数类型
     */
    private String type;
    /**
     * 参数值
     */
    private Object value;

    @Override
    public String toString() {
        return JsonUtil.toJson(this);
    }
}
