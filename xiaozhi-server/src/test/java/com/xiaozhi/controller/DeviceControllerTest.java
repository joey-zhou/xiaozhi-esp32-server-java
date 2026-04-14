package com.xiaozhi.controller;

import com.xiaozhi.device.DeviceController;
import com.xiaozhi.common.model.req.DeviceBatchUpdateReq;
import com.xiaozhi.common.model.req.DevicePageReq;
import com.xiaozhi.common.model.req.DeviceUpdateReq;
import com.xiaozhi.common.model.resp.DeviceResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.common.web.ResultStatus;
import com.xiaozhi.device.DeviceAppService;
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

        verify(deviceAppService).update(eq("dev-1"), any(DeviceUpdateReq.class));
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
    void otaActivateReturnsAcceptedWhenDeviceIdInvalid() throws Exception {
        when(deviceAppService.checkOtaActivation("bad-device")).thenReturn(false);

        mockMvc.perform(post("/api/device/ota/activate").header("Device-Id", "bad-device"))
            .andExpect(status().isAccepted());
    }
}
