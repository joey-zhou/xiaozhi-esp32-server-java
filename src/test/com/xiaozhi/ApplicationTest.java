package com.xiaozhi;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public class ApplicationTest {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationTest.class);
    @Autowired
    private SysDeviceService sysDeviceService;
    
    @Test
    public void sysDeviceServiceUpdate() {
        // 手动启动事务
         try {
            SysDevice device = new SysDevice();
            device.setState(SysDevice.DEVICE_STATE_OFFLINE);
            // 不设置 deviceId，这样会更新所有设备
            int updatedRows = sysDeviceService.update(device);
            logger.info("项目启动，重置 {} 个设备状态为离线", updatedRows);
         } catch (Exception e) {
             throw e;
        } finally {
         }
    }

}