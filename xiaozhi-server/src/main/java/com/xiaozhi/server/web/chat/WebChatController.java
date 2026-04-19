package com.xiaozhi.server.web.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.model.ChatToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Web 聊天 API：通过 SSE 提供流式文本对话。
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Web 聊天", description = "Web 端文本聊天相关操作")
public class WebChatController {

    @Resource
    private WebChatService webChatService;

    /**
     * 开启聊天会话。
     * 不传 {@code sessionId} 时创建新会话；传入已有 sessionId 时尝试续接（会校验归属）。
     *
     * @param roleId    角色 ID
     * @param sessionId 可选，续接的会话 ID
     * @return sessionId
     */
    @PostMapping("/open")
    @SaCheckPermission("system:chat:api:open")
    @Operation(summary = "开启聊天会话", description = "创建或续接 Web 聊天会话并返回 sessionId")
    public Map<String, String> open(@RequestParam Integer roleId,
                                    @RequestParam(required = false) String sessionId) {
        Integer userId = StpUtil.getLoginIdAsInt();
        String openedSessionId = webChatService.openSession(userId, roleId, sessionId);
        return Map.of("sessionId", openedSessionId);
    }

    /**
     * 流式聊天（SSE）
     *
     * @param sessionId 会话ID
     * @param text      用户消息
     * @return AI 回复文本流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("system:chat:api:stream")
    @Operation(summary = "流式聊天", description = "通过 SSE 返回 AI 回复 Token 流，包含 thinking 和 content 两种类型")
    public Flux<ChatToken> stream(@RequestParam String sessionId, @RequestParam String text) {
        return webChatService.chatStream(sessionId, text);
    }

    /**
     * 关闭聊天会话
     */
    @PostMapping("/close")
    @SaCheckPermission("system:chat:api:close")
    @Operation(summary = "关闭聊天会话", description = "关闭 Web 聊天会话并释放资源")
    public Map<String, String> close(@RequestParam String sessionId) {
        webChatService.closeSession(sessionId);
        return Map.of("status", "closed");
    }
}
