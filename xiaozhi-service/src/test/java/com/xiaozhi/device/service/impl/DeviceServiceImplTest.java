package com.xiaozhi.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaozhi.common.CacheHelper;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.device.convert.DeviceConvert;
import com.xiaozhi.device.dal.mysql.dataobject.DeviceDO;
import com.xiaozhi.device.dal.mysql.mapper.DeviceMapper;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住设备查询的缓存穿透路径与激活码生成：
 * 设备号里的冒号在缓存键上要换成连字符，命中缓存时不得回源；
 * 已有有效验证码时直接复用，否则新插入一个六位数字码。
 */
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

    @Mock
    private Cache cache;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    @Test
    void getBOReturnsNullWithoutTouchingCacheWhenDeviceIdBlank() {
        assertThat(deviceService.getBO(" ")).isNull();

        verifyNoInteractions(cacheManager, cacheHelper, deviceMapper, deviceConvert);
    }

    @Test
    void getBOLoadsFromDbAndWritesBackToCacheOnMiss() {
        DeviceDO deviceDO = new DeviceDO();
        deviceDO.setDeviceId("00:11:22");
        DeviceBO deviceBO = new DeviceBO();

        when(cacheManager.getCache(DeviceService.CACHE_NAME)).thenReturn(cache);
        when(cache.get("00-11-22", DeviceBO.class)).thenReturn(null);
        stubCacheHelperPreferringCache();
        when(deviceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(deviceDO);
        when(deviceConvert.toBO(deviceDO)).thenReturn(deviceBO);

        assertThat(deviceService.getBO("00:11:22")).isSameAs(deviceBO);

        // 冒号是 Redis 的层级分隔符，缓存键与锁键都必须先替换成连字符
        verify(cacheHelper).getWithLock(eq("device:00-11-22"), any(), any());
        verify(cache).put("00-11-22", deviceBO);

        ArgumentCaptor<LambdaQueryWrapper<DeviceDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("deviceId =");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactly("00:11:22");
    }

    @Test
    void getBOReturnsCachedValueWithoutQueryingDb() {
        DeviceBO cached = new DeviceBO();

        when(cacheManager.getCache(DeviceService.CACHE_NAME)).thenReturn(cache);
        when(cache.get("00-11-22", DeviceBO.class)).thenReturn(cached);
        stubCacheHelperPreferringCache();

        assertThat(deviceService.getBO("00:11:22")).isSameAs(cached);

        verifyNoInteractions(deviceMapper, deviceConvert);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void listByStateAndTypeAppliesOnlyNonBlankFilters() {
        DeviceDO deviceDO = new DeviceDO();
        DeviceBO deviceBO = new DeviceBO();

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(deviceDO));
        when(deviceConvert.toBO(deviceDO)).thenReturn(deviceBO);

        List<DeviceBO> result = deviceService.listByStateAndType("1", " ");

        assertThat(result).containsExactly(deviceBO);

        ArgumentCaptor<LambdaQueryWrapper<DeviceDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(deviceMapper).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("state =").doesNotContain("type =");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactly("1");
    }

    @Test
    void generateCodeReusesExistingValidCode() {
        VerifyCodeBO existing = new VerifyCodeBO();
        existing.setCode("123456");

        when(deviceMapper.selectValidCode(null, "device-1", "session-1")).thenReturn(existing);

        assertThat(deviceService.generateCode("device-1", "session-1", "bind")).isSameAs(existing);

        verify(deviceMapper, never()).insertVerifyCode(any(), any(), any(), any());
    }

    @Test
    void generateCodeInsertsSixDigitCodeWhenNoneValid() {
        VerifyCodeBO created = new VerifyCodeBO();

        when(deviceMapper.selectValidCode(nullable(String.class), eq("device-1"), eq("session-1")))
            .thenReturn(null, created);

        assertThat(deviceService.generateCode("device-1", "session-1", "bind")).isSameAs(created);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(deviceMapper).insertVerifyCode(eq("device-1"), eq("session-1"), eq("bind"), codeCaptor.capture());
        // 验证码需左侧补零到固定六位，否则设备端按定长解析会取错
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    void updateCodeAudioPathReturnsZeroWhenAnyArgumentBlank() {
        assertThat(deviceService.updateCodeAudioPath(" ", "session-1", "123456", "/a.wav")).isZero();
        assertThat(deviceService.updateCodeAudioPath("device-1", " ", "123456", "/a.wav")).isZero();
        assertThat(deviceService.updateCodeAudioPath("device-1", "session-1", " ", "/a.wav")).isZero();
        assertThat(deviceService.updateCodeAudioPath("device-1", "session-1", "123456", " ")).isZero();

        verifyNoInteractions(deviceMapper);
    }

    /** getBO 走缓存包装，测试里让它先读缓存、未命中再回源。 */
    private void stubCacheHelperPreferringCache() {
        when(cacheHelper.getWithLock(anyString(), any(), any())).thenAnswer(invocation -> {
            Supplier<DeviceBO> cacheSupplier = invocation.getArgument(1);
            Supplier<DeviceBO> dbSupplier = invocation.getArgument(2);
            DeviceBO cached = cacheSupplier.get();
            return cached != null ? cached : dbSupplier.get();
        });
    }
}
