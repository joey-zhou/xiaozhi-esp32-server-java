package com.xiaozhi.server.web.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.model.req.ChatStreamReq;
import com.xiaozhi.common.web.ApiResponse;
import com.xiaozhi.common.model.resp.ChatSessionClosedResp;
import com.xiaozhi.common.model.resp.ChatSessionOpenedResp;
import com.xiaozhi.common.model.resp.ChatTokenResp;
import com.xiaozhi.server.web.chat.convert.WebChatConvert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Web 聊天 API：通过 SSE 提供流式文本对话。
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Web 聊天", description = "Web 端文本聊天相关操作")
public class WebChatController {

    @Resource
    private WebChatAppService webChatAppService;

    @Resource
    private WebChatConvert webChatConvert;

    /**
     * 开启聊天会话。
     * 不传 {@code sessionId} 时创建新会话；传入已有 sessionId 时尝试续接（会校验归属）。
     * roleId 归属由 {@code @CheckOwner} 拦下：角色的 roleDesc 是用户私有的提示词，
     * 不校验就能用别人的 roleId 开会话并读到它。
     *
     * @param roleId    角色 ID
     * @param sessionId 可选，续接的会话 ID
     * @return sessionId
     */
    @PostMapping("/open")
    @SaCheckPermission("system:chat:api:open")
    @CheckOwner(resource = "role", id = "#roleId")
    @Operation(summary = "开启聊天会话", description = "创建或续接 Web 聊天会话并返回 sessionId")
    public ApiResponse<ChatSessionOpenedResp> open(@RequestParam Integer roleId,
                                     @RequestParam(required = false) String sessionId) {
        Integer userId = StpUtil.getLoginIdAsInt();
        return ApiResponse.success(ChatSessionOpenedResp.of(webChatAppService.openSession(userId, roleId, sessionId)));
    }

    /**
     * 流式聊天（SSE）。
     * 用户输入走请求体，不放 query，避免被 access log 与反向代理日志留存。
     *
     * @param req 会话 ID 与用户消息
     * @return AI 回复文本流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("system:chat:api:stream")
    @Operation(summary = "流式聊天", description = "通过 SSE 返回 AI 回复 Token 流，包含 thinking 和 content 两种类型")
    public Flux<ChatTokenResp> stream(@Valid @RequestBody ChatStreamReq req) {
        Integer userId = StpUtil.getLoginIdAsInt();
        return webChatAppService.chatStream(req.getSessionId(), req.getText(), userId).map(webChatConvert::toResp);
    }

    /**
     * 关闭聊天会话
     */
    @PostMapping("/close")
    @SaCheckPermission("system:chat:api:close")
    @Operation(summary = "关闭聊天会话", description = "关闭 Web 聊天会话并释放资源")
    public ApiResponse<ChatSessionClosedResp> close(@RequestParam String sessionId) {
        Integer userId = StpUtil.getLoginIdAsInt();
        webChatAppService.closeSession(sessionId, userId);
        return ApiResponse.success(ChatSessionClosedResp.closed());
    }
}
