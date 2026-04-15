package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.dialogue.runtime.Persona;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SessionExitFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final String TOOL_NAME = "exit_session";

    @Resource
    private SessionManager sessionManager;

    ToolCallback toolCallback = FunctionToolCallback
            .builder(TOOL_NAME, (Map<String, String> params, ToolContext toolContext) -> {
                String sessionId = (String) toolContext.getContext().get(Persona.TOOL_CONTEXT_SESSION_ID_KEY);
                ChatSession chatSession = sessionManager.getSession(sessionId);
                chatSession.getPlayer().setFunctionAfterChat(()->sessionManager.closeSession(chatSession));
                String sayGoodbye = params.get("sayGoodbye");
                if(sayGoodbye == null || sayGoodbye.trim().isEmpty()){
                    sayGoodbye = "好的，再见！期待下次聊天哦！";
                }
                return sayGoodbye;
            })
            .toolMetadata(new XiaozhiToolMetadata(true))
            .description("当用户明确表达要离开/结束对话时调用此函数。触发词汇：'拜拜'、'再见'、'退下'、'走了'、'结束对话'、'退出'、'我要走了'、'goodbye'、'bye'。重要：检测到这些词汇时必须调用此函数来正确结束会话，不要只是普通回复。")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "sayGoodbye": {
                                    "type": "string",
                                    "description": "告别语"
                                }
                            },
                            "required": ["sayGoodbye"]
                        }
                    """)
            .inputType(Map.class)
            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
            .build();

    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        return toolCallback;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public String getToolDescription() {
        return "会话退出";
    }
}
