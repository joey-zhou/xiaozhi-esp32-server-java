package com.xiaozhi.ai.llm.factory;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视觉识别服务。
 * 封装多模态视觉模型调用，供 MCP 工具或 REST 接口使用。
 */
@Service
public class VisionService {

    private static final Logger logger = LoggerFactory.getLogger(VisionService.class);

    @Resource
    private ChatModelFactory chatModelFactory;

    /**
     * 识别图片内容。
     *
     * @param file     图片文件
     * @param question 用户提问
     * @return 视觉模型返回的文本描述
     * @throws IllegalStateException 无可用的视觉模型
     */
    public String recognize(MultipartFile file, String question) {
        ChatModel chatModel = chatModelFactory.getVisionModel();
        if (chatModel == null) {
            throw new IllegalStateException("无可用的视觉模型");
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

        String result = chatModel.call(userMessage);
        logger.info("视觉识别完成 - 问题: {}, 结果: {}", question, result);
        return result;
    }
}
