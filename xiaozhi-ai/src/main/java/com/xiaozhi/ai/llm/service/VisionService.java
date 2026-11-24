package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.model.bo.ChatImageBO;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.List;

/**
 * 看图回答：设备拍照与网页聊天发图共用
 */
@Slf4j
@Service
public class VisionService {

    @Resource
    private ChatModelFactory chatModelFactory;
    @Resource
    private DeviceService deviceService;
    @Resource
    private RoleService roleService;

    /** 设备拍照：设备当前角色的模型能看图就由它看，否则由默认视觉模型看 */
    public String recognize(String deviceId, ChatImageBO image, String question) {
        RoleBO role = roleOf(deviceId);
        ChatModel model = role != null && chatModelFactory.resolveConfig(role).acceptsImages()
                ? chatModelFactory.getChatModel(role)
                : chatModelFactory.getVisionModel();
        return ask(model, List.of(image), question);
    }

    /** 由默认视觉模型看图 */
    public String describe(List<ChatImageBO> images, String question) {
        return ask(chatModelFactory.getVisionModel(), images, question);
    }

    public static Media mediaOf(ChatImageBO image) {
        return Media.builder()
                .mimeType(MimeType.valueOf(image.mimeType()))
                .data(new ByteArrayResource(image.content()))
                .build();
    }

    private String ask(ChatModel model, List<ChatImageBO> images, String question) {
        UserMessage userMessage = UserMessage.builder()
                .media(images.stream().map(VisionService::mediaOf).toList())
                .text(question)
                .build();
        String result = ChatClient.builder(model)
                .defaultAdvisors()
                .build()
                .prompt()
                .messages(userMessage)
                .call()
                .content();
        log.info("视觉识别完成 - 图片: {} 张, 问题: {}, 结果: {}", images.size(), question, result);
        return result;
    }

    private RoleBO roleOf(String deviceId) {
        DeviceBO device = deviceId == null ? null : deviceService.getBO(deviceId);
        return device == null || device.getRoleId() == null ? null : roleService.getBO(device.getRoleId());
    }
}
