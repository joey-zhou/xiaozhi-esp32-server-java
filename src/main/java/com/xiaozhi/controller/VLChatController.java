package com.xiaozhi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.security.annotation.AnonymousAccess;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 视觉对话
 */
@RestController
@RequestMapping("/api/vl")
@Tag(name = "视觉对话管理", description = "视觉对话相关操作")
public class VLChatController extends BaseController {

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private SessionManager sessionManager;

    /**
     * 视觉对话
     */
    @AnonymousAccess
    @PostMapping(value = "/chat", produces = "application/json;charset=UTF-8")
    @Operation(summary = "图片识别", description = "根据问题返回识别结果")
    public String vlChat(
        @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
        @Parameter(description = "问题") @RequestParam String question,
        HttpServletRequest request) {
        try {
            //获取当前下发的session信息
            String authorization = request.getHeader("authorization");
            logger.info("用户Authorization：{}", authorization);

            // 检查 Authorization header 是否存在
            if (authorization == null || authorization.isEmpty()) {
                logger.error("Authorization header 不存在");
                return createErrorResponse("缺少认证信息");
            }

            // 检查是否是 Bearer token 格式
            if (!authorization.startsWith("Bearer ")) {
                logger.error("Authorization header 格式错误: {}", authorization);
                return createErrorResponse("认证格式错误，应为: Bearer <token>");
            }

            //下发的是session
            String sessionId = authorization.substring(7);

            ChatSession session = sessionManager.getSession(sessionId);
            if (session == null) {
                logger.error("session不存在: {}", sessionId);
                return createErrorResponse("session不存在");
            }

            ChatModel chatModel = chatModelFactory.takeVisionModel();
            if (chatModel == null) {
                logger.error("无可用的视觉模型");
                return createErrorResponse("无可用的视觉模型");
            }

            MimeType mimeType = MimeType.valueOf(file.getContentType());
            Media media = Media.builder()
                    .mimeType(mimeType)
                    .data(file.getResource())
                    .build();

            UserMessage userMessage = UserMessage.builder()
                    .media(media)
                    .text(question)
                    .build();
            String call = chatModel.call(userMessage);
            logger.info("问题：{}，图文识别内容：{}", question, call);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("text", call);
            String string = new ObjectMapper().writeValueAsString(result);
            logger.info("json结果:{}", string);

            return string;
        } catch (Exception e) {
            logger.error("视觉对话处理失败", e);
            return createErrorResponse("视觉对话处理失败: " + e.getMessage());
        }
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(String errorMessage) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", errorMessage);
            return new ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"success\":false,\"error\":\"" + errorMessage + "\"}";
        }
    }
}