package com.xiaozhi.device;

import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.req.DeviceScanBindReq;
import com.xiaozhi.common.model.req.OtaReq;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.communication.auth.DeviceAuthService;
import com.xiaozhi.communication.registry.DialogueServerRegistry;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.domain.repository.DeviceRepository;
import com.xiaozhi.device.domain.vo.VerifyCode;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAppServiceTest {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";

    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceConvert deviceConvert;
    @Mock
    private RoleService roleService;
    @Mock
    private ServerAddressProvider serverAddressProvider;
    @Mock
    private DialogueServerRegistry dialogueServerRegistry;
    @Mock
    private DeviceAuthService deviceAuthService;

    @InjectMocks
    private DeviceAppService deviceAppService;

    @BeforeEach
    void setUp() {
        DeviceResp boundDevice = new DeviceResp();
        boundDevice.setDeviceId(DEVICE_ID);
        lenient().when(deviceService.get(DEVICE_ID)).thenReturn(boundDevice);
    }

    @Test
    void handleOtaIssuesWebsocketTokenAndProtocolVersion() {
        when(deviceAuthService.generateDeviceToken(DEVICE_ID)).thenReturn("sig.123");
        ReflectionTestUtils.setField(deviceAppService, "websocketProtocolVersion", 2);

        Map<String, Object> response = deviceAppService.handleOta(otaRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> websocket = (Map<String, Object>) response.get("websocket");
        assertThat(websocket).containsEntry("token", "sig.123")
                .containsEntry("version", 2);
    }

    @Test
    void scanBindCreatesDeviceWhenUnboundAndRecentlyOnline() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceRepository.findVerifyCode(null, DEVICE_ID, null))
                .thenReturn(Optional.of(verifyCode("toy-v1")));
        RoleBO role = new RoleBO();
        role.setRoleId(3);
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role);

        // 贴纸上是大写 '-' 分隔的 MAC，应归一化为设备上报的小写冒号格式
        DeviceResp result = deviceAppService.scanBind(scanBindReq("AA-BB-CC-DD-EE-FF"), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(7);
        assertThat(captor.getValue().getRoleId()).isEqualTo(3);
        assertThat(captor.getValue().getDeviceName()).isEqualTo("toy-v1");
        verify(deviceRepository).invalidateVerifyCodes(DEVICE_ID);
    }

    @Test
    void scanBindReturnsExistingDeviceWhenAlreadyBoundToSameUser() {
        Device existing = Device.newDevice(DEVICE_ID, "小智", null, 7, 3);
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));

        DeviceResp result = deviceAppService.scanBind(scanBindReq(DEVICE_ID), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void scanBindRejectsWhenBoundToOtherUser() {
        Device existing = Device.newDevice(DEVICE_ID, "小智", null, 8, 3);
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> deviceAppService.scanBind(scanBindReq(DEVICE_ID), 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("设备已被其他用户绑定");
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void scanBindRejectsWhenDeviceNotRecentlyOnline() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceRepository.findVerifyCode(null, DEVICE_ID, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceAppService.scanBind(scanBindReq(DEVICE_ID), 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("设备不在线");
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void scanBindRejectsInvalidMacAddress() {
        assertThatThrownBy(() -> deviceAppService.scanBind(scanBindReq("not-a-mac"), 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("设备ID不正确");
    }

    private DeviceScanBindReq scanBindReq(String deviceId) {
        DeviceScanBindReq req = new DeviceScanBindReq();
        req.setDeviceId(deviceId);
        return req;
    }

    private VerifyCode verifyCode(String type) {
        return new VerifyCode("123456", DEVICE_ID, null, type, null, LocalDateTime.now());
    }

    private OtaReq otaRequest() {
        OtaReq req = new OtaReq();
        req.setDeviceId(DEVICE_ID);
        return req;
    }
}
