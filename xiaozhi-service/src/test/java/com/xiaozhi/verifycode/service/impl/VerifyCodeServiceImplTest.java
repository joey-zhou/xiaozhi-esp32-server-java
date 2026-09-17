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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住验证码的 10 分钟有效期：起点在 Java 侧算，查询共用同一个常量；
 * 设备侧三个过滤条件非空才参与查询，全为空时直接拒绝；
 * 账号验证码校验即消费，同一账号的尝试次数有上限。
 */
@ExtendWith(MockitoExtension.class)
class VerifyCodeServiceImplTest {

    private static final Duration VALID_WINDOW = Duration.ofMinutes(10);
    private static final String ATTEMPT_KEY = "xiaozhi:captcha:attempt:a@b.com";

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(VerifyCodeDO.class);
    }

    @Mock
    private VerifyCodeMapper verifyCodeMapper;

    @Mock
    private VerifyCodeConvert verifyCodeConvert;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private VerifyCodeServiceImpl verifyCodeService;

    @Test
    void findValidRefusesToQueryWhenAllFiltersBlank() {
        assertThat(verifyCodeService.findValid(null, null, "")).isNull();

        verifyNoInteractions(verifyCodeMapper);
    }

    @Test
    void findValidAppliesAllThreeFiltersWhenPresent() {
        LocalDateTime beforeCall = LocalDateTime.now();
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
        assertThat(validSinceOf(captor.getValue()))
            .isBetween(beforeCall.minus(VALID_WINDOW), LocalDateTime.now().minus(VALID_WINDOW));
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
    void findValidExcludesAccountCodes() {
        when(verifyCodeMapper.selectOne(any())).thenReturn(null);

        assertThat(verifyCodeService.findValid("123456", null, null)).isNull();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).selectOne(captor.capture());
        // 设备码与账号码同表，漏掉 account IS NULL 会把账号码当成设备码返回
        assertThat(captor.getValue().getTargetSql()).contains("account IS NULL");
    }

    @Test
    void findValidByCodeRefusesToQueryWhenCodeBlank() {
        assertThat(verifyCodeService.findValidByCode("")).isNull();

        verifyNoInteractions(verifyCodeMapper);
    }

    @Test
    void findValidByCodeReturnsDeviceCodeWhenExactlyOneMatch() {
        VerifyCodeDO matched = verifyCode(LocalDateTime.now().minusMinutes(1));
        VerifyCodeBO bo = new VerifyCodeBO();
        when(verifyCodeMapper.selectList(any())).thenReturn(List.of(matched));
        when(verifyCodeConvert.toBO(matched)).thenReturn(bo);

        assertThat(verifyCodeService.findValidByCode("123456")).isSameAs(bo);

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).selectList(captor.capture());
        // 只凭 code 定位设备，多取一条就是为了判断有没有撞码
        assertThat(captor.getValue().getTargetSql())
            .contains("code =")
            .contains("account IS NULL")
            .contains("LIMIT 2");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("123456");
    }

    @Test
    void findValidByCodeReturnsNullWhenCodeHitsMoreThanOneDevice() {
        when(verifyCodeMapper.selectList(any()))
            .thenReturn(List.of(verifyCode(LocalDateTime.now().minusMinutes(1)),
                verifyCode(LocalDateTime.now().minusMinutes(2))));

        // 撞码时无法判定属于哪台设备，绑错设备比拒绝绑定严重得多
        assertThat(verifyCodeService.findValidByCode("123456")).isNull();

        verifyNoInteractions(verifyCodeConvert);
    }

    @Test
    void consumeByAccountDeletesMatchedCodeAndResetsAttempts() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(1L);
        when(verifyCodeMapper.delete(any())).thenReturn(1);

        assertThat(verifyCodeService.consumeByAccount("a@b.com", "123456")).isTrue();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("account =")
            .contains("code =")
            .contains("createTime >=");
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains("a@b.com", "123456");
        assertThat(validSinceOf(captor.getValue()))
            .isBetween(beforeCall.minus(VALID_WINDOW), LocalDateTime.now().minus(VALID_WINDOW));
        verify(stringRedisTemplate).expire(ATTEMPT_KEY, VALID_WINDOW);
        verify(stringRedisTemplate).delete(ATTEMPT_KEY);
    }

    @Test
    void consumeByAccountIsFalseWhenNothingDeleted() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(2L);
        when(verifyCodeMapper.delete(any())).thenReturn(0);

        assertThat(verifyCodeService.consumeByAccount("a@b.com", "123456")).isFalse();

        verify(verifyCodeMapper).delete(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void consumeByAccountWipesRemainingCodesOnLastAllowedAttempt() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(5L);
        when(verifyCodeMapper.delete(any())).thenReturn(0);

        assertThat(verifyCodeService.consumeByAccount("a@b.com", "000000")).isFalse();

        // 一次消费尝试 + 一次把该账号剩下的码全部作废
        verify(verifyCodeMapper, times(2)).delete(any());
    }

    @Test
    void consumeByAccountStopsQueryingAfterAttemptsExhausted() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(ATTEMPT_KEY)).thenReturn(6L);

        assertThat(verifyCodeService.consumeByAccount("a@b.com", "123456")).isFalse();

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("account =")
            .doesNotContain("code =");
    }

    @Test
    void consumeByAccountReturnsFalseWithoutSideEffectWhenArgumentBlank() {
        assertThat(verifyCodeService.consumeByAccount(" ", "123456")).isFalse();
        assertThat(verifyCodeService.consumeByAccount("a@b.com", " ")).isFalse();

        verifyNoInteractions(verifyCodeMapper, stringRedisTemplate);
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
        assertThat(captor.getValue().getAccount()).isNull();
        assertThat(captor.getValue().getCreateTime()).isBetween(beforeCall, LocalDateTime.now());
    }

    @Test
    void generateForAccountDropsPreviousCodesAndAttemptsOfSameAccount() {
        LocalDateTime beforeCall = LocalDateTime.now();
        when(verifyCodeMapper.insert(any(VerifyCodeDO.class))).thenReturn(1);

        String code = verifyCodeService.generateForAccount("a@b.com");

        assertThat(code).matches("\\d{6}");

        ArgumentCaptor<LambdaQueryWrapper<VerifyCodeDO>> deleteCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(verifyCodeMapper).delete(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().getTargetSql()).contains("account =");
        assertThat(deleteCaptor.getValue().getParamNameValuePairs().values()).containsExactly("a@b.com");
        verify(stringRedisTemplate).delete(ATTEMPT_KEY);

        ArgumentCaptor<VerifyCodeDO> captor = ArgumentCaptor.forClass(VerifyCodeDO.class);
        verify(verifyCodeMapper).insert(captor.capture());
        assertThat(captor.getValue().getAccount()).isEqualTo("a@b.com");
        assertThat(captor.getValue().getCode()).isEqualTo(code);
        assertThat(captor.getValue().getDeviceId()).isNull();
        assertThat(captor.getValue().getCreateTime()).isBetween(beforeCall, LocalDateTime.now());
    }

    @Test
    void generateForAccountRejectsBlankAccountWithoutTouchingStorage() {
        assertThatThrownBy(() -> verifyCodeService.generateForAccount(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("账号不能为空");

        verifyNoInteractions(verifyCodeMapper, stringRedisTemplate);
    }

    @Test
    void generateForAccountThrowsWhenInsertMissed() {
        when(verifyCodeMapper.insert(any(VerifyCodeDO.class))).thenReturn(0);

        // 落库没成功还把码发出去，用户拿着一个库里查不到的码，怎么填都错
        assertThatThrownBy(() -> verifyCodeService.generateForAccount("a@b.com"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("生成验证码失败");
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
