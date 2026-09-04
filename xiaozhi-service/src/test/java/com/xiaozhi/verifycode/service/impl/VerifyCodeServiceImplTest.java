package com.xiaozhi.verifycode.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.model.bo.VerifyCodeBO;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.verifycode.convert.VerifyCodeConvert;
import com.xiaozhi.verifycode.dal.mysql.dataobject.VerifyCodeDO;
import com.xiaozhi.verifycode.dal.mysql.mapper.VerifyCodeMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住验证码的 10 分钟有效期：起点在 Java 侧算，两个查询共用同一个常量；
 * 设备侧三个过滤条件非空才参与查询，全为空时只剩有效期一条。
 */
@ExtendWith(MockitoExtension.class)
class VerifyCodeServiceImplTest {

    private static final Duration VALID_WINDOW = Duration.ofMinutes(10);

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(VerifyCodeDO.class);
    }

    @Mock
    private VerifyCodeMapper verifyCodeMapper;

    @Mock
    private VerifyCodeConvert verifyCodeConvert;

    @InjectMocks
    private VerifyCodeServiceImpl verifyCodeService;

    @Test
    void findValidQueriesByExpiryOnlyWhenAllFiltersBlank() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(verifyCodeMapper.selectOne(any())).thenReturn(null);

        assertThat(verifyCodeService.findValid(null, null, "")).isNull();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("createTime >=")
            .doesNotContain("deviceId =")
            .doesNotContain("sessionId =")
            .doesNotContain("code =");
        assertThat(validSinceOf(captor.getValue()))
            .isBetween(beforeCall.minus(VALID_WINDOW), LocalDateTime.now().minus(VALID_WINDOW));
    }

    @Test
    void findValidAppliesAllThreeFiltersWhenPresent() {
        when(verifyCodeMapper.selectOne(any())).thenReturn(null);

        assertThat(verifyCodeService.findValid("123456", "device-1", "session-1")).isNull();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("deviceId =")
            .contains("sessionId =")
            .contains("code =")
            .contains("ORDER BY createTime DESC");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .contains("device-1", "session-1", "123456");
    }

    @Test
    void findValidKeepsWhitespaceOnlyFilter() {
        when(verifyCodeMapper.selectOne(any())).thenReturn(null);

        assertThat(verifyCodeService.findValid(" ", null, null)).isNull();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("code =");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(" ");
    }

    @Test
    void findValidReturnsCodeCreatedInsideValidWindow() {
        VerifyCodeDO fresh = verifyCode(LocalDateTime.now().minusMinutes(9));
        VerifyCodeBO bo = new VerifyCodeBO();
        when(verifyCodeMapper.selectOne(any())).thenAnswer(onlyIfInsideWindow(fresh));
        when(verifyCodeConvert.toBO(fresh)).thenReturn(bo);

        assertThat(verifyCodeService.findValid("123456", "device-1", "session-1")).isSameAs(bo);
    }

    @Test
    void findValidSkipsCodeCreatedBeforeValidWindow() {
        VerifyCodeDO expired = verifyCode(LocalDateTime.now().minusMinutes(11));
        when(verifyCodeMapper.selectOne(any())).thenAnswer(onlyIfInsideWindow(expired));

        assertThat(verifyCodeService.findValid("123456", "device-1", "session-1")).isNull();
    }

    @Test
    void hasValidEmailIsTrueForCodeInsideValidWindow() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(verifyCodeMapper.exists(any())).thenAnswer(existsIfInsideWindow(LocalDateTime.now().minusMinutes(9)));

        assertThat(verifyCodeService.hasValidEmail("a@b.com", "123456")).isTrue();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).exists(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("email =")
            .contains("code =")
            .contains("createTime >=");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("a@b.com", "123456");
        assertThat(validSinceOf(captor.getValue()))
            .isBetween(beforeCall.minus(VALID_WINDOW), LocalDateTime.now().minus(VALID_WINDOW));
    }

    @Test
    void hasValidEmailIsFalseForCodeBeforeValidWindow() {
        when(verifyCodeMapper.exists(any())).thenAnswer(existsIfInsideWindow(LocalDateTime.now().minusMinutes(11)));

        assertThat(verifyCodeService.hasValidEmail("a@b.com", "123456")).isFalse();
    }

    @Test
    void createForDeviceWritesDeviceColumnsWithCreateTime() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(verifyCodeMapper.insert(any(VerifyCodeDO.class))).thenReturn(1);

        assertThat(verifyCodeService.createForDevice("device-1", "session-1", "bind", "123456")).isEqualTo(1);

        ArgumentCaptor<VerifyCodeDO> captor = ArgumentCaptor.forClass(VerifyCodeDO.class);
        verify(verifyCodeMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo("device-1");
        assertThat(captor.getValue().getSessionId()).isEqualTo("session-1");
        assertThat(captor.getValue().getType()).isEqualTo("bind");
        assertThat(captor.getValue().getCode()).isEqualTo("123456");
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getCreateTime()).isBetween(beforeCall, LocalDateTime.now());
    }

    @Test
    void createForEmailWritesEmailColumnsWithCreateTime() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(verifyCodeMapper.insert(any(VerifyCodeDO.class))).thenReturn(1);

        assertThat(verifyCodeService.createForEmail("a@b.com", "123456")).isEqualTo(1);

        ArgumentCaptor<VerifyCodeDO> captor = ArgumentCaptor.forClass(VerifyCodeDO.class);
        verify(verifyCodeMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("a@b.com");
        assertThat(captor.getValue().getCode()).isEqualTo("123456");
        assertThat(captor.getValue().getDeviceId()).isNull();
        assertThat(captor.getValue().getCreateTime()).isBetween(beforeCall, LocalDateTime.now());
    }

    @Test
    void deleteByDeviceIdFiltersByDeviceIdOnly() {
        when(verifyCodeMapper.delete(any())).thenReturn(2);

        assertThat(verifyCodeService.deleteByDeviceId("device-1")).isEqualTo(2);

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("deviceId =");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactly("device-1");
    }

    @Test
    void updateAudioPathMatchesDeviceSessionAndCode() {
        when(verifyCodeMapper.update(isNull(), any())).thenReturn(1);

        assertThat(verifyCodeService.updateAudioPath("device-1", "session-1", "123456", "/a.wav")).isEqualTo(1);

        ArgumentCaptor<LambdaUpdateWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(verifyCodeMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("deviceId =")
            .contains("sessionId =")
            .contains("code =");
        assertThat(captor.getValue().getParamNameValuePairs().values())
            .contains("device-1", "session-1", "123456", "/a.wav");
    }

    private static VerifyCodeDO verifyCode(LocalDateTime createTime) {
        VerifyCodeDO verifyCode = new VerifyCodeDO();
        verifyCode.setCode("123456");
        verifyCode.setDeviceId("device-1");
        verifyCode.setSessionId("session-1");
        verifyCode.setCreateTime(createTime);
        return verifyCode;
    }

    /** 用查询条件里的有效期起点过滤给定记录，替代库侧的时间比较。 */
    private static Answer<VerifyCodeDO> onlyIfInsideWindow(VerifyCodeDO verifyCode) {
        return invocation -> verifyCode.getCreateTime().isBefore(validSinceOf(invocation.getArgument(0)))
            ? null : verifyCode;
    }

    private static Answer<Boolean> existsIfInsideWindow(LocalDateTime createTime) {
        return invocation -> !createTime.isBefore(validSinceOf(invocation.getArgument(0)));
    }

    /** 取出条件里唯一的时间参数，即服务算出的有效期起点。 */
    private static LocalDateTime validSinceOf(Wrapper<VerifyCodeDO> wrapper) {
        AbstractWrapper<?, ?, ?> abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
        // MyBatis-Plus 的条件片段惰性求值，不先取一次 SQL，paramNameValuePairs 是空的
        abstractWrapper.getTargetSql();
        return abstractWrapper.getParamNameValuePairs().values().stream()
            .filter(LocalDateTime.class::isInstance)
            .map(LocalDateTime.class::cast)
            .findFirst()
            .orElseThrow();
    }
}
