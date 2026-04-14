package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// @Component
public class PlayListGetter implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(PlayListGetter.class);
    public static final String TOOL_NAME = "get_playlist";

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Tool(name = TOOL_NAME, description = "获取可播放的歌曲列表",returnDirect = false)
    public String getPlayList() {
        try {
            Path playlistPath = Path.of(runtimePathConfig.getMusicDir(), "playlist.txt");
            return Files.readString(playlistPath);
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
