package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Component
public class PlayListGetter implements ToolsGlobalRegistry.GlobalFunction {
    public static final String TOOL_NAME = "get_playlist";
    private static final int MAX_PLAYLIST_CHARS = 2000;

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Tool(name = TOOL_NAME, description = "获取可播放的歌曲列表",returnDirect = false)
    public String getPlayList() {
        try {
            Path playlistPath = Path.of(runtimePathConfig.getMusicDir(), "playlist.txt");
            String playlist = Files.readString(playlistPath);
            // 整份列表会进 LLM 上下文，超长截断
            return playlist.length() > MAX_PLAYLIST_CHARS ? playlist.substring(0, MAX_PLAYLIST_CHARS) : playlist;
        } catch (IOException e) {
            return "目前没有可播放的歌曲列表";
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
        return "获取歌曲列表";
    }
}
