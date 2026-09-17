package com.xiaozhi.device;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.common.model.req.DeviceCreateReq;
import com.xiaozhi.common.model.req.DeviceScanBindReq;
import com.xiaozhi.common.model.req.DeviceUpdateReq;
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
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.role.service.RoleService;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.utils.CmsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAppServiceTest {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);

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
    @Mock
    private MessageService messageService;
    @Mock
    private SummaryService summaryService;

    private DeviceAppService deviceAppService;

    @BeforeEach
    void setUp() {
        deviceAppService = new DeviceAppService();
        ReflectionTestUtils.setField(deviceAppService, "deviceService", deviceService);
        ReflectionTestUtils.setField(deviceAppService, "deviceRepository", deviceRepository);
        ReflectionTestUtils.setField(deviceAppService, "deviceConvert", deviceConvert);
        ReflectionTestUtils.setField(deviceAppService, "roleService", roleService);
        ReflectionTestUtils.setField(deviceAppService, "serverAddressProvider", serverAddressProvider);
        ReflectionTestUtils.setField(deviceAppService, "dialogueServerRegistry", dialogueServerRegistry);
        ReflectionTestUtils.setField(deviceAppService, "deviceAuthService", deviceAuthService);
        ReflectionTestUtils.setField(deviceAppService, "messageService", messageService);
        ReflectionTestUtils.setField(deviceAppService, "summaryService", summaryService);
        DeviceBO boundDevice = new DeviceBO();
        boundDevice.setDeviceId(DEVICE_ID);
        boundDevice.setDeviceName("客厅音箱");
        lenient().when(deviceService.getBO(DEVICE_ID)).thenReturn(boundDevice);
        lenient().when(serverAddressProvider.getWebsocketAddress()).thenReturn("ws://server.test/xiaozhi/v1/");
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
    void handleOtaSyncsBoundDevice() {
        OtaReq req = otaRequest();
        req.setIp("10.0.0.8");
        when(deviceRepository.findById(DEVICE_ID))
                .thenReturn(Optional.of(Device.newDevice(DEVICE_ID, "客厅音箱", "dual-board", 7, 3)));

        Map<String, Object> response = deviceAppService.handleOta(req);

        assertThat(response).containsKey("websocket").doesNotContainKey("activation");
        verify(deviceRepository).save(any(Device.class));
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
    }

    @Test
    void createBindsDeviceLocatedByCodeAndInvalidatesRemainingCodes() {
        when(deviceRepository.findVerifyCodeByCode("123456")).thenReturn(Optional.of(verifyCode("toy-v1")));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role(3, "小智"));

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
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role(3, "小智"));

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
        when(roleService.getBO(3)).thenReturn(role(3, "管家"));

        DeviceResp result = deviceAppService.scanBind(scanBindReq(DEVICE_ID), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        // 幂等返回同样不再回查设备表，角色名按主键单独取
        assertThat(result.getRoleName()).isEqualTo("管家");
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

    @Test
    void createReturnsEveryResponseFieldFromTheAggregate() {
        when(deviceRepository.findVerifyCodeByCode("123456")).thenReturn(Optional.of(verifyCode("toy-v1")));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role(3, "小智"));
        stampOnSave(CREATED_AT, CREATED_AT);

        DeviceResp result = deviceAppService.create(createReq("123456"), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.getDeviceName()).isEqualTo("toy-v1");
        assertThat(result.getRoleId()).isEqualTo(3);
        assertThat(result.getRoleName()).isEqualTo("小智");
        assertThat(result.getType()).isEqualTo("toy-v1");
        assertThat(result.getState()).isEqualTo(Device.STATE_OFFLINE);
        assertThat(result.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(result.getUpdateTime()).isEqualTo(CREATED_AT);
        // 新设备还没上报过硬件信息，这几列本来就是空的
        assertThat(result.getIp()).isNull();
        assertThat(result.getLocation()).isNull();
        assertThat(result.getWifiName()).isNull();
        assertThat(result.getChipModelName()).isNull();
        assertThat(result.getVersion()).isNull();
        // sessionId/code/audioPath/mcpList 从来不在这几个写接口的出参里
        assertThat(result.getSessionId()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getAudioPath()).isNull();
        assertThat(result.getMcpList()).isNull();
        // 出参全部来自聚合根：设备表只在幂等判断时读了一次，写完不再回读
        verify(deviceRepository).findById(DEVICE_ID);
    }

    @Test
    void scanBindReturnsEveryResponseFieldFromTheAggregate() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceRepository.findVerifyCode(null, DEVICE_ID, null))
                .thenReturn(Optional.of(verifyCode("toy-v1")));
        when(roleService.getDefaultOrFirstBO(7)).thenReturn(role(3, "小智"));
        stampOnSave(CREATED_AT, CREATED_AT);

        DeviceResp result = deviceAppService.scanBind(scanBindReq(DEVICE_ID), 7);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.getDeviceName()).isEqualTo("toy-v1");
        assertThat(result.getRoleId()).isEqualTo(3);
        assertThat(result.getRoleName()).isEqualTo("小智");
        assertThat(result.getType()).isEqualTo("toy-v1");
        assertThat(result.getState()).isEqualTo(Device.STATE_OFFLINE);
        assertThat(result.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(result.getUpdateTime()).isEqualTo(CREATED_AT);
        assertThat(result.getIp()).isNull();
        assertThat(result.getLocation()).isNull();
        assertThat(result.getWifiName()).isNull();
        assertThat(result.getChipModelName()).isNull();
        assertThat(result.getVersion()).isNull();
        assertThat(result.getSessionId()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getAudioPath()).isNull();
        assertThat(result.getMcpList()).isNull();
        verify(deviceRepository).findById(DEVICE_ID);
    }

    @Test
    void updateReturnsEveryResponseFieldFromTheAggregate() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(storedDevice()));
        when(roleService.getBO(9)).thenReturn(role(9, "管家"));
        stampOnSave(null, UPDATED_AT);

        DeviceUpdateReq req = new DeviceUpdateReq();
        req.setDeviceName("书房音箱");
        req.setRoleId(9);
        req.setLocation("上海");

        DeviceResp result = deviceAppService.update(DEVICE_ID, req);

        assertThat(result.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.getDeviceName()).isEqualTo("书房音箱");
        assertThat(result.getRoleId()).isEqualTo(9);
        assertThat(result.getRoleName()).isEqualTo("管家");
        assertThat(result.getLocation()).isEqualTo("上海");
        assertThat(result.getState()).isEqualTo("1");
        assertThat(result.getWifiName()).isEqualTo("home");
        assertThat(result.getIp()).isEqualTo("10.0.0.8");
        assertThat(result.getChipModelName()).isEqualTo("esp32s3");
        assertThat(result.getType()).isEqualTo("dual-board");
        assertThat(result.getVersion()).isEqualTo("2.4.0");
        assertThat(result.getCreateTime()).isEqualTo(CREATED_AT);
        // 更新时间取本次写库实际落的值，不是聚合根加载时那份
        assertThat(result.getUpdateTime()).isEqualTo(UPDATED_AT);
        assertThat(result.getSessionId()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getAudioPath()).isNull();
        // 设备表里存着 mcpList，但这个接口本来就不返回它
        assertThat(result.getMcpList()).isNull();
        // 校验角色时已经查过角色，出参不再多查一次
        verify(roleService).getBO(9);
    }

    @Test
    void updateWithoutRoleChangeStillCarriesCurrentRoleName() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(storedDevice()));
        when(roleService.getBO(3)).thenReturn(role(3, "小智"));
        stampOnSave(null, UPDATED_AT);

        DeviceUpdateReq req = new DeviceUpdateReq();
        req.setDeviceName("书房音箱");

        DeviceResp result = deviceAppService.update(DEVICE_ID, req);

        assertThat(result.getRoleId()).isEqualTo(3);
        assertThat(result.getRoleName()).isEqualTo("小智");
    }

    @Test
    void deleteThrowsWhenDeviceNotFoundWithoutTouchingRelatedData() {
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceAppService.delete(DEVICE_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(deviceRepository, never()).delete(any());
        verify(messageService, never()).deleteByDeviceId(any());
    }

    /** 库里已有的一台设备，字段全带值，用于钉住写接口出参一个字段都不少 */
    private Device storedDevice() {
        return new Device(DEVICE_ID, "客厅音箱", 7, 3, "[\"tool\"]", "10.0.0.8", "北京",
                "home", "esp32s3", "dual-board", "2.4.0", "1",
                CREATED_AT, LocalDateTime.of(2026, 2, 1, 8, 0));
    }

    private RoleBO role(Integer roleId, String roleName) {
        RoleBO role = new RoleBO();
        role.setRoleId(roleId);
        role.setRoleName(roleName);
        role.setUserId(7);
        return role;
    }

    /** 模拟仓储 save()：自动填充生成的时间戳在落库后被回填进聚合根 */
    private void stampOnSave(LocalDateTime createTime, LocalDateTime updateTime) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Device.class).markPersisted(createTime, updateTime);
            return null;
        }).when(deviceRepository).save(any(Device.class));
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
