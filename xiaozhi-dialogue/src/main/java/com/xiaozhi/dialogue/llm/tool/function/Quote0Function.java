package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import okhttp3.OkHttpClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
/**
 * Quote0更新
 */
@Slf4j
// @Component
public class Quote0Function implements ToolsGlobalRegistry.GlobalFunction {
    private static final String TOOL_NAME = "update_eink";
    private static final String API_BASE_URL = "https://dot.mindreset.tech/api/open/text";

    // 使用OkHttp3替代JDK HttpClient
    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        return FunctionToolCallback
                .builder(TOOL_NAME, (Map<String, String> params, ToolContext toolContext) -> {
                    String title = params.get("title");
                    String message = params.get("message");
                    String signature = params.get("signature");
                    try {
                        // 构建JSON请求体
                        String jsonBody = String.format("""
                                {
                                    "refreshNow": true,
                                    "deviceId": "xxx",
                                    "title": "%s",
                                    "message": "%s",
                                    "signature": "%s",
                                    "icon": "",
                                    "link": ""
                                }
                                """, title, message, signature);

                        // 构建HTTP请求
                        okhttp3.Request request = new okhttp3.Request.Builder()
                                .url(API_BASE_URL)
                                .post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                                .addHeader("Authorization", "Bearer xxx") // 需要替换为实际的API密钥
                                .addHeader("Content-Type", "application/json")
                                .build();

                        // 执行请求
                        try (okhttp3.Response response = okHttpClient.newCall(request).execute()) {
                            if (response.isSuccessful()) {
                                return "墨水屏信息更新成功";
                            } else {
                                log.error("墨水屏更新失败，HTTP状态码: {}", response.code());
                                return "墨水屏更新失败，状态码: " + response.code();
                            }
                        }
                    } catch (Exception e) {
                        log.error("墨水屏更新异常", e);
                        return "墨水屏更新异常: " + e.getMessage();
                    }
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .description("更新墨水屏信息，当前的时间是：" + LocalDateTime.now())
                .inputSchema("""
                            {
                                "type": "object",
                                "properties": {
                                    "title": {
                                        "type": "string",
                                        "description": "更新的标题，一般是日程提醒"
                                    },
                                    "message": {
                                        "type": "string",
                                        "description": "需要展示的信息"
                                    },
                                    "signature": {
                                        "type": "string",
                                        "description": "需要关注的时间，格式：yyyy-MM-dd HH:mm:ss"
                                    }
                                },
                                "required": ["title","message","signature"]
                            }
                        """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public String getToolDescription() {
        return "更新墨水屏";
    }
}
