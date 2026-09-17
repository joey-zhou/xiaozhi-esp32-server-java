package com.xiaozhi.device;

import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.req.DeviceCreateReq;
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
import com.xiaozhi.device.model.DeviceProjection;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.utils.CmsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
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
    private final DeviceConvert deviceConvert = Mappers.getMapper(DeviceConvert.class);
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
        ReflectionTestUtils.setField(deviceAppService, "deviceConvert", deviceConvert);
        DeviceBO boundDevice = new DeviceBO();
        boundDevice.setDeviceId(DEVICE_ID);
        boundDevice.setDeviceName("客厅音箱");
        lenient().when(deviceService.getBO(DEVICE_ID)).thenReturn(boundDevice);
        DeviceProjection projection = new DeviceProjection();
        projection.setDeviceId(DEVICE_ID);
        projection.setRoleName("小智");
        lenient().when(deviceService.get(DEVICE_ID)).thenReturn(projection);
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
    void handleOtaIssuesActivationCodeWhenDeviceUnbound() {
        when(deviceService.getBO(DEVICE_ID)).thenReturn(null);
        VerifyCodeBO code = new VerifyCodeBO();
        code.setCode("123456");
        when(deviceService.generateCode(DEVICE_ID, null, "dual-board")).thenReturn(code);

        Map<String, Object> response = deviceAppService.handleOta(otaRequest());

        @SuppressWarnings("unchecked")
        Map<String, Object> activation = (Map<String, Object>) response.get("activation");
        assertThat(activation).containsEntry("code", "123456").containsEntry("challenge", DEVICE_ID);
        assertThat(response).containsKey("websocket");
        verify(deviceService, never()).get(any());
    }

    @Test
    void handleOtaSyncsBoundDeviceWithoutReadingProjection() {
        OtaReq req = otaRequest();
        req.setIp("10.0.0.8");
        when(deviceRepository.findById(DEVICE_ID))
                .thenReturn(Optional.of(Device.newDevice(DEVICE_ID, "客厅音箱", "dual-board", 7, 3)));

        Map<String, Object> response = deviceAppService.handleOta(req);

        assertThat(response).containsKey("websocket").doesNotContainKey("activation");
        verify(deviceRepository).save(any(Device.class));
        verify(deviceService, never()).get(any());
    }

    @Test
    void handleOtaRejectsInvalidDeviceIdBeforeResolvingIpLocation() {
        OtaReq req = new OtaReq();
        req.setDeviceId("not-a-mac");
        req.setIp("203.0.113.7");

        try (MockedStatic<CmsUtils> cmsUtils = mockStatic(CmsUtils.class)) {
            assertThatThrownBy(() -> deviceAppService.handleOta(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("设备ID不正确");
            // 设备 ID 先过校验再解析 IP 归属，未注册请求带不动 OTA 主链路
            cmsUtils.verifyNoInteractions();
        }
    }

    @Test
    void handleOtaResolvesIpLocationFromCacheWithoutBlockingLookup() {
        OtaReq req = otaRequest();
        req.setIp("203.0.113.7");

        try (MockedStatic<CmsUtils> cmsUtils = mockStatic(CmsUtils.class)) {
            cmsUtils.when(() -> CmsUtils.getIPInfoFromCache("203.0.113.7"))
                    .thenReturn(new CmsUtils.IPInfo("203.0.113.7", "广东省深圳市", "电信"));

            deviceAppService.handleOta(req);

            assertThat(req.getLocation()).isEqualTo("广东省深圳市");
            // 主链路只读本地缓存，阻塞版外呼不能出现在 OTA 上
            cmsUtils.verify(() -> CmsUtils.getIPInfoByAddress(any()), never());
        }
    }

    @Test
    void checkOtaActivationReadsBoundDeviceFromBO() {
        assertThat(deviceAppService.checkOtaActivation(DEVICE_ID)).isTrue();

        when(deviceService.getBO(DEVICE_ID)).thenReturn(null);
        assertThat(deviceAppService.checkOtaActivation(DEVICE_ID)).isFalse();
        verify(deviceService, never()).get(any());
    }

    @Test
    void createBindsDeviceLocatedByCodeAndInvalidatesRemainingCodes() {
        when(deviceRepository.findVerifyCodeByCode("123456")).thenReturn(Optional.of(verifyCode("toy-v1")));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        RoleBO role = new RoleBO();
        role.setRoleId(3);
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role);

        DeviceResp result = deviceAppService.create(createReq("123456"), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7);
        assertThat(captor.getValue().getRoleId()).isEqualTo(3);
        // 绑定用掉的码必须立即失效，否则同一个 6 位码在有效期内还能被继续试
        verify(deviceRepository).invalidateVerifyCodes(DEVICE_ID);
    }

    @Test
    void createRejectsWhenCodeLocatesNoSingleDevice() {
        when(deviceRepository.findVerifyCodeByCode("123456")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceAppService.create(createReq("123456"), 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无效验证码");
        verify(deviceRepository, never()).save(any());
        verify(deviceRepository, never()).invalidateVerifyCodes(any());
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
        assertThat(result.getRoleName()).isEqualTo("小智");
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

    private DeviceCreateReq createReq(String code) {
        DeviceCreateReq req = new DeviceCreateReq();
        req.setCode(code);
        return req;
    }

    private VerifyCode verifyCode(String type) {
        return new VerifyCode("123456", DEVICE_ID, null, type, null, LocalDateTime.now());
    }

    private OtaReq otaRequest() {
        OtaReq req = new OtaReq();
        req.setDeviceId(DEVICE_ID);
        req.setType("dual-board");
        return req;
    }
}
