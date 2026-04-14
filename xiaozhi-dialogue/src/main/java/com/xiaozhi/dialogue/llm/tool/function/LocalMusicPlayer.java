package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.dialogue.runtime.Persona;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// @Component
public class LocalMusicPlayer implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(LocalMusicPlayer.class);
    public static final String TOOL_NAME = "play_music";

    // 使用虚拟线程执行器处理定时任务
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            Thread.ofVirtual().name("music-scheduler-", 0).factory());

    @Resource
    private SessionManager sessionManager;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Tool(name = TOOL_NAME, description = "音乐播放器,播放指定名称的歌曲", returnDirect = true)
    public String playMusic(@ToolParam(description = "要播放的歌曲名称") String songName, ToolContext toolContext) {
        String sessionId = (String) toolContext.getContext().get(Persona.TOOL_CONTEXT_SESSION_ID_KEY);
        ChatSession chatSession = sessionManager.getSession(sessionId);

        try {
            if (songName == null || songName.isEmpty()) {
                return "你没有告诉我具体的歌曲名称，我播放不了！";
            } else {
                scheduler.schedule(() -> {
                    // 必须异步处理，也就是先返回一个回应用户的字符串，再开始播放。
                    chatSession.getPlayer().play(songName, Path.of(runtimePathConfig.getMusicDir(), songName + ".mp3"));
                }, 60, TimeUnit.MILLISECONDS);
                return "尝试播放歌曲《" + songName + "》";
            }

        } catch (Exception e) {
            logger.error("device 音乐播放异常，song name: {}", songName, e);
            return "音乐播放失败";
        }
    }


    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        ToolCallback[] tools = ToolCallbacks.from(this);
        return tools[0];
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public String getToolDescription() {
        return "播放音乐";
    }
}
