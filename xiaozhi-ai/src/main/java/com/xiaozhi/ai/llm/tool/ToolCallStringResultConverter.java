package com.xiaozhi.ai.llm.tool;

import com.xiaozhi.utils.JsonUtil;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

public class ToolCallStringResultConverter implements ToolCallResultConverter {

    public static final ToolCallStringResultConverter INSTANCE = new ToolCallStringResultConverter();

    private ToolCallStringResultConverter() {
        // Private constructor to enforce singleton pattern
    }

    @Override
    public String convert(Object result, Type returnType) {
        if (result instanceof String s) {
            return s;
        }
        return JsonUtil.toJson(result);
    }
}
