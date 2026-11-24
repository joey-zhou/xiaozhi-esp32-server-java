package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.common.model.bo.ChatImageBO;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 设备拍照：角色模型能看图就由它看，否则由默认视觉模型看 */
@ExtendWith(MockitoExtension.class)
class VisionServiceTest {

    @Mock
    private ChatModelFactory chatModelFactory;
    @Mock
    private DeviceService deviceService;
    @Mock
    private RoleService roleService;
    @Mock
    private ChatModel roleModel;
    @Mock
    private ChatModel visionModel;

    @InjectMocks
    private VisionService visionService;

    private final ChatImageBO photo = new ChatImageBO("image/jpeg", new byte[]{1});

    private RoleBO deviceRole() {
        DeviceBO device = new DeviceBO();
        device.setRoleId(3);
        RoleBO role = new RoleBO();
        role.setRoleId(3);
        when(deviceService.getBO("d1")).thenReturn(device);
        when(roleService.getBO(3)).thenReturn(role);
        return role;
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void theRoleModelLooksWhenItCanSeeImages() {
        RoleBO role = deviceRole();
        when(chatModelFactory.resolveConfig(role)).thenReturn(new ConfigBO().setImageInput(true));
        when(chatModelFactory.getChatModel(role)).thenReturn(roleModel);
        when(roleModel.call(any(Prompt.class))).thenReturn(answer("一只猫"));

        assertThat(visionService.recognize("d1", photo, "这是什么")).isEqualTo("一只猫");
        verify(chatModelFactory, never()).getVisionModel();
    }

    @Test
    void theDefaultVisionModelLooksOtherwise() {
        RoleBO role = deviceRole();
        when(chatModelFactory.resolveConfig(role)).thenReturn(new ConfigBO().setImageInput(false));
        when(chatModelFactory.getVisionModel()).thenReturn(visionModel);
        when(visionModel.call(any(Prompt.class))).thenReturn(answer("一只狗"));

        assertThat(visionService.recognize("d1", photo, "这是什么")).isEqualTo("一只狗");
        verify(chatModelFactory, never()).getChatModel(any());
    }

    @Test
    void anUnknownDeviceUsesTheDefaultVisionModel() {
        when(chatModelFactory.getVisionModel()).thenReturn(visionModel);
        when(visionModel.call(any(Prompt.class))).thenReturn(answer("一只狗"));

        assertThat(visionService.recognize("ghost", photo, "这是什么")).isEqualTo("一只狗");
    }
}
