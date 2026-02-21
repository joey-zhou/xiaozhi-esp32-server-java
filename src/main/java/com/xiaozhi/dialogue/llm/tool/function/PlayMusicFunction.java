package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.service.MessageService;
import com.xiaozhi.dialogue.service.MusicPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//@Component
public class PlayMusicFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(PlayMusicFunction.class);
    // 使用虚拟线程执行器处理定时任务
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            Thread.ofVirtual().name("music-scheduler-", 0).factory());
    @Autowired
    private MessageService messageService;

    ToolCallback toolCallback = FunctionToolCallback
            .builder("func_playMusic", (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession)toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                String songName = params.get("songName");
                try{
                    if (songName == null || songName.isEmpty()) {
                        return "音乐播放失败";
                    }else{
                        scheduler.schedule(() -> {
                            // 必须异步处理，也就是先返回一个回应用户的字符串，再开始播放。
                            new MusicPlayer(chatSession,songName, null).play();
                        },60, TimeUnit.MILLISECONDS);

                        return "尝试播放歌曲《"+songName+"》";
                    }
                }catch (Exception e){
                    logger.error("device 音乐播放异常，song name: {}", songName, e);
                    return "音乐播放失败";
                }
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
            .description("音乐播放助手,需要用户提供歌曲的名称")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "songName": {
                                    "type": "string",
                                    "description": "要播放的歌曲名称"
                                }
                            },
                            "required": ["songName"]
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
