package com.xiaozhi.device;

import com.xiaozhi.common.model.req.DeviceBatchUpdateReq;
import com.xiaozhi.common.model.req.DevicePageReq;
import com.xiaozhi.common.model.req.DeviceScanBindReq;
import com.xiaozhi.common.model.req.DeviceUpdateReq;
import com.xiaozhi.common.model.req.OtaReq;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.support.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 钉住设备接口的路由与请求绑定：分页/扫码绑定/更新的字段绑定、必填校验文案，
 * 以及 OTA 上报体（含 EG800AK 这类非标准结构）到 OtaReq 的解析。
 */
@ExtendWith(MockitoExtension.class)
class DeviceControllerTest extends ControllerTestSupport {

    private MockMvc mockMvc;

    @Mock
    private DeviceAppService deviceAppService;

    private DeviceController deviceController;

    @BeforeEach
    void setUp() {
        deviceController = new DeviceController();
        ReflectionTestUtils.setField(deviceController, "deviceAppService", deviceAppService);
        mockMvc = buildMockMvc(deviceController);
    }

    @Test
    void queryReturnsPagedDevicesForCurrentUser() throws Exception {
        DeviceResp resp = new DeviceResp();
        resp.setDeviceId("dev-1");
        resp.setDeviceName("客厅音箱");
        PageResp<DeviceResp> pageResp = new PageResp<>(List.of(resp), 1L, 1, 10);
        when(deviceAppService.page(any(DevicePageReq.class), eq(7))).thenReturn(pageResp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(get("/api/device")
                    .param("pageNo", "1")
                    .param("pageSize", "10")
                    .param("deviceName", "客厅"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.list[0].deviceId").value("dev-1"));
        }

        ArgumentCaptor<DevicePageReq> captor = ArgumentCaptor.forClass(DevicePageReq.class);
        verify(deviceAppService).page(captor.capture(), eq(7));
        assertThat(captor.getValue().getDeviceName()).isEqualTo("客厅");
    }

    @Test
    void batchUpdateReturnsSuccessCountAndTotalCount() throws Exception {
        when(deviceAppService.batchUpdate(any(DeviceBatchUpdateReq.class)))
            .thenReturn(Map.of("successCount", 2, "totalCount", 2));

        mockMvc.perform(post("/api/device/batchUpdate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"deviceIds":"dev-1,dev-2","roleId":3}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
            .andExpect(jsonPath("$.data.successCount").value(2))
            .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void scanBindDelegatesToAppService() throws Exception {
        DeviceResp resp = new DeviceResp();
        resp.setDeviceId("aa:bb:cc:dd:ee:ff");
        when(deviceAppService.scanBind(any(DeviceScanBindReq.class), eq(7))).thenReturn(resp);

        try (var ignored = mockLoginUser(7)) {
            mockMvc.perform(post("/api/device/scan-bind")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"deviceId":"AA-BB-CC-DD-EE-FF"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultStatus.SUCCESS))
                .andExpect(jsonPath("$.data.deviceId").value("aa:bb:cc:dd:ee:ff"));
        }

        ArgumentCaptor<DeviceScanBindReq> captor = ArgumentCaptor.forClass(DeviceScanBindReq.class);
        verify(deviceAppService).scanBind(captor.capture(), eq(7));
        assertThat(captor.getValue().getDeviceId()).isEqualTo("AA-BB-CC-DD-EE-FF");
    }

    @Test
    void scanBindReturnsBadRequestWhenDeviceIdBlank() throws Exception {
        mockMvc.perform(post("/api/device/scan-bind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"deviceId":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("设备ID不能为空"));
    }

    @Test
    void updateDelegatesToAppService() throws Exception {
        DeviceResp updatedDevice = new DeviceResp();
        updatedDevice.setDeviceId("dev-1");
        updatedDevice.setRoleId(2);
        when(deviceAppService.update(eq("dev-1"), any(DeviceUpdateReq.class))).thenReturn(updatedDevice);

        mockMvc.perform(put("/api/device/dev-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"roleId":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleId").value(2));

        ArgumentCaptor<DeviceUpdateReq> captor = ArgumentCaptor.forClass(DeviceUpdateReq.class);
        verify(deviceAppService).update(eq("dev-1"), captor.capture());
        assertThat(captor.getValue().getRoleId()).isEqualTo(2);
    }

    @Test
    void otaReturnsBadRequestWhenDeviceIdInvalid() throws Exception {
        when(deviceAppService.handleOta(any())).thenThrow(new IllegalArgumentException("设备ID不正确"));

        mockMvc.perform(post("/api/device/ota")
                .header("Device-Id", "bad-device")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("设备ID不正确"));
    }

    @Test
    void otaParsesEg800akDeviceInfo() throws Exception {
        when(deviceAppService.handleOta(any())).thenReturn(Map.of(
            "firmware", Map.of("url", "https://example.test/eg800ak.bin", "version", "2.4.0")
        ));

        mockMvc.perform(post("/api/device/ota")
                .header("Device-Id", "aa:bb:cc:dd:ee:ff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "chip_model_name": "ASR1605",
                      "application": {"version": "2.3.0"},
                      "board": {"type": "EG800AK"}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firmware.version").value("2.4.0"));

        ArgumentCaptor<OtaReq> captor = ArgumentCaptor.forClass(OtaReq.class);
        verify(deviceAppService).handleOta(captor.capture());
        assertThat(captor.getValue().getChipModelName()).isEqualTo("ASR1605");
        assertThat(captor.getValue().getVersion()).isEqualTo("2.3.0");
        assertThat(captor.getValue().getType()).isEqualTo("EG800AK");
    }

    @Test
    void otaActivateReturnsAcceptedWhenDeviceIdInvalid() throws Exception {
        when(deviceAppService.checkOtaActivation("bad-device")).thenReturn(false);

        mockMvc.perform(post("/api/device/ota/activate").header("Device-Id", "bad-device"))
            .andExpect(status().isAccepted());
    }
}
