package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.dialogue.runtime.Persona;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Component
public class LocalMusicPlayer implements ToolsGlobalRegistry.GlobalFunction {
    public static final String TOOL_NAME = "play_music";
    private static final String MUSIC_SUFFIX = ".mp3";

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
        if (chatSession == null || chatSession.getPlayer() == null) {
            return "音乐播放失败";
        }
        if (songName == null || songName.isEmpty()) {
            return "你没有告诉我具体的歌曲名称，我播放不了！";
        }

        try {
            Path musicFile = resolveMusicFile(songName);
            if (musicFile == null) {
                return "我这里没有《" + songName + "》这首歌";
            }
            scheduler.schedule(() -> {
                // 必须异步处理，也就是先返回一个回应用户的字符串，再开始播放。
                chatSession.getPlayer().play(songName, musicFile);
            }, 60, TimeUnit.MILLISECONDS);
            return "尝试播放歌曲《" + songName + "》";

        } catch (Exception e) {
            log.error("device 音乐播放异常，song name: {}", songName, e);
            return "音乐播放失败";
        }
    }

    /**
     * 歌名解析到音乐目录下的 mp3 文件。歌名可能已带 .mp3（来自歌曲列表），
     * 解析结果必须仍在音乐目录内，文件不存在返回 null。
     */
    private Path resolveMusicFile(String songName) {
        String fileName = songName.endsWith(MUSIC_SUFFIX) ? songName : songName + MUSIC_SUFFIX;
        Path musicDir = runtimePathConfig.resolveMusicDir();
        Path musicFile = musicDir.resolve(fileName).normalize();
        if (!musicFile.startsWith(musicDir) || !Files.isRegularFile(musicFile)) {
            return null;
        }
        return musicFile;
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
