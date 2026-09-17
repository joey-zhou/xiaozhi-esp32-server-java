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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 save() 的写入顺序与时间戳回填。
 * <p>save() 是 upsert：先更新，匹配不到行才插入。更新是常态（上下线上报、改角色、同步都走它），
 * 反过来先插入会让每次更新都先撞一次主键冲突。
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

    private Device existingDevice() {
        Device device = new Device(DEVICE_ID, "客厅音箱", 7, 3, null, "10.0.0.8", "北京",
                "home", "esp32s3", "dual-board", "2.4.0", "1",
                CREATED_AT, LocalDateTime.of(2026, 2, 1, 8, 0));
        device.update("书房音箱", null, null);
        return device;
    }

    @Test
    void updateHitsOnFirstWriteAndNeverFallsBackToInsert() {
        when(deviceMapper.updateById(any(DeviceDO.class))).thenAnswer(invocation -> {
            DeviceDO updated = invocation.getArgument(0);
            // 更新语句不能带 createTime，否则会覆盖设备原有的创建时间
            assertThat(updated.getCreateTime()).isNull();
            updated.setUpdateTime(UPDATED_AT);
            return 1;
        });

        Device device = existingDevice();
        repository.save(device);

        verify(deviceMapper, never()).insert(any(DeviceDO.class));
        assertThat(device.getCreateTime()).isEqualTo(CREATED_AT);
        assertThat(device.getUpdateTime()).isEqualTo(UPDATED_AT);
    }

    @Test
    void insertRunsWhenUpdateMatchesNoRowAndWritesBackBothTimestamps() {
        when(deviceMapper.updateById(any(DeviceDO.class))).thenReturn(0);
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
    void concurrentFirstRegistrationFallsBackToUpdateWithoutOverwritingCreateTime() {
        AtomicInteger updateCalls = new AtomicInteger();
        when(deviceMapper.updateById(any(DeviceDO.class))).thenAnswer(invocation -> {
            DeviceDO updated = invocation.getArgument(0);
            if (updateCalls.getAndIncrement() == 0) {
                return 0;
            }
            // 撞主键后退回更新，自动填充写上的 createTime 必须已被清掉
            assertThat(updated.getCreateTime()).isNull();
            updated.setUpdateTime(UPDATED_AT);
            return 1;
        });
        when(deviceMapper.insert(any(DeviceDO.class))).thenAnswer(invocation -> {
            DeviceDO inserted = invocation.getArgument(0);
            // 自动填充在语句发出前就写了 createTime，冲突失败后它仍留在 DO 上
            inserted.setCreateTime(LocalDateTime.of(2026, 9, 12, 20, 0));
            throw new DuplicateKeyException("duplicate");
        });

        Device device = Device.newDevice(DEVICE_ID, "客厅音箱", "dual-board", 7, 3);
        repository.save(device);

        assertThat(updateCalls.get()).isEqualTo(2);
        assertThat(device.getUpdateTime()).isEqualTo(UPDATED_AT);
    }
}
