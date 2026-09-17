package com.xiaozhi.device.infrastructure;

import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.domain.Device;
import com.xiaozhi.device.infrastructure.convert.DeviceConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 钉住 save() 把本次写库的时间戳回填进聚合根。
 * <p>createTime / updateTime 由 MyBatis-Plus 的自动填充在写库时塞进 DO，设备的写接口靠这次回填
 * 直接出参；一旦不回填，创建设备的返回里两个时间就会变成 null，只能再查一遍设备表补回来。
 */
@ExtendWith(MockitoExtension.class)
class DeviceRepositoryTimestampTest {

    private static final String DEVICE_ID = "aa:bb:cc:dd:ee:ff";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 8, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeviceRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new DeviceRepositoryImpl();
        ReflectionTestUtils.setField(repository, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(repository, "deviceConverter", new DeviceConverter());
        ReflectionTestUtils.setField(repository, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(repository, "eventPublisher", eventPublisher);
    }

    @Test
    void insertWritesBackBothTimestampsToTheAggregate() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);
        when(deviceMapper.insert(any(DeviceDO.class))).thenAnswer(invocation -> {
            DeviceDO inserted = invocation.getArgument(0);
            inserted.setCreateTime(CREATED_AT);
            inserted.setUpdateTime(CREATED_AT);
            return 1;
        });

        Device device = Device.newDevice(DEVICE_ID, "客厅音箱", "dual-board", 7, 3);
        repository.save(device);

        assertThat(device.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(device.getUpdateTime()).isEqualTo(CREATED_AT);
    }

    @Test
    void updateWritesBackNewUpdateTimeAndKeepsCreateTime() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(new DeviceDO());
        when(deviceMapper.updateById(any(DeviceDO.class))).thenAnswer(invocation -> {
            DeviceDO updated = invocation.getArgument(0);
            updated.setUpdateTime(UPDATED_AT);
            return 1;
        });

        Device device = new Device(DEVICE_ID, "客厅音箱", 7, 3, null, "10.0.0.8", "北京",
                "home", "esp32s3", "dual-board", "2.4.0", "1",
                CREATED_AT, LocalDateTime.of(2026, 2, 1, 8, 0));
        device.update("书房音箱", null, null);
        repository.save(device);

        // 更新语句不带 createTime，回填时不能把聚合根上的创建时间清掉
        assertThat(device.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(device.getUpdateTime()).isEqualTo(UPDATED_AT);
    }
}
