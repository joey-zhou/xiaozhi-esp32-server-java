package com.xiaozhi.communication.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.ai.llm.service.VisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 视觉对话（MCP 识图接口）
 * 由设备 MCP 客户端调用，Bearer token 为 sessionId。
 */
@RestController
@RequestMapping("/api/vl")
@Tag(name = "视觉对话管理", description = "视觉对话相关操作")
public class VLChatController {

    private static final Logger logger = LoggerFactory.getLogger(VLChatController.class);

    @Resource
    private VisionService visionService;

    @Resource
    private SessionManager sessionManager;

    @SaIgnore
    @PostMapping(value = "/chat", produces = "application/json;charset=UTF-8")
    @Operation(summary = "图片识别", description = "根据问题返回识别结果")
    public Map<String, Object> vlChat(
        @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
        @Parameter(description = "问题") @RequestParam String question,
        HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return failure("图片不能为空");
        }
        if (!StringUtils.hasText(question)) {
            return failure("问题不能为空");
        }

        String authorization = request.getHeader("authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return failure("缺少认证信息或格式错误");
        }

        String sessionId = authorization.substring(7);
        var session = sessionManager.getSession(sessionId);
        if (session == null) {
            return failure("session不存在");
        }

        try {
            String result = visionService.recognize(file, question);
            return success(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("视觉对话请求失败, sessionId={}, error={}", sessionId, e.getMessage());
            return failure(e.getMessage());
        } catch (RuntimeException e) {
            logger.error("视觉对话处理失败, sessionId={}", sessionId, e);
            return failure("视觉对话处理失败，请稍后重试");
        }
    }

    private Map<String, Object> success(String text) {
        return Map.of("success", true, "text", text);
    }

    private Map<String, Object> failure(String message) {
        return Map.of("success", false, "error", message);
    }
}
