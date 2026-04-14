package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.runtime.Persona;
import com.xiaozhi.ai.llm.memory.Conversation;
import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 创建一个新的对话
 */
@Component
public class NewChatFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final String TOOL_NAME = "new_chat";

    ToolCallback toolCallback = FunctionToolCallback
            .builder(TOOL_NAME, (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession) toolContext.getContext().get(Persona.TOOL_CONTEXT_SESSION_ID_KEY);
                Conversation conversation = chatSession.getPersona().getConversation();
                conversation.clear();
                String sayNewChat = params.get("sayNewChat");
                if (sayNewChat == null) {
                    sayNewChat = "让我们聊聊新的话题吧！";
                }
                return sayNewChat;
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
            .description("当用户想开启新的对话调用function：new_chat")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "sayNewChat": {
                                    "type": "string",
                                    "description": "与用户友好开心新对话的开场语"
                                }
                            },
                            "required": ["sayNewChat"]
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
        return "新对话";
    }
}
