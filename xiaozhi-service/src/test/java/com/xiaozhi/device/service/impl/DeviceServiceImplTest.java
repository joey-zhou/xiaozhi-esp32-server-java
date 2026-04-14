package com.xiaozhi.device.service.impl;

import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(DeviceDO.class);
    }

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceConvert deviceConvert;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CacheHelper cacheHelper;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        lenient().when(cacheHelper.getWithLock(any(), any(), any()))
            .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());
    }

}
