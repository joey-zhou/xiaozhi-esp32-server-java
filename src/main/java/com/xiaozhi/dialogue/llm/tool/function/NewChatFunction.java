package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
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

    ToolCallback toolCallback = FunctionToolCallback
            .builder("func_new_chat", (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                Conversation conversation = chatSession.getConversation();
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
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        return toolCallback;
    }
}
