package com.xiaozhi.summary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.summary.convert.SummaryConvert;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import com.xiaozhi.summary.dal.mysql.mapper.SummaryMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住会话摘要的入参防御、默认值补齐与删除条件：
 * summaryId 是毫秒时间戳，删除时必须转成 createTime 等值条件，不传则不加该条件。
 */
@ExtendWith(MockitoExtension.class)
class SummaryServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(SummaryDO.class);
    }

    @Mock
    private SummaryMapper summaryMapper;

    @Mock
    private SummaryConvert summaryConvert;

    @InjectMocks
    private SummaryServiceImpl summaryService;

    // 分页由 XML 的 JOIN 直出 BO（含设备名、角色名），Service 只透传条件与分页参数
    @Test
    void pagePassesScopeThroughAndReturnsRowsAsIs() {
        SummaryBO summaryBO = new SummaryBO();
        Page<SummaryBO> page = new Page<>(2, 5);
        page.setRecords(List.of(summaryBO));
        page.setTotal(7);
        when(summaryMapper.selectPage(any(Page.class), eq("device-1"), eq(9), eq(1))).thenReturn(page);

        var result = summaryService.page("device-1", 9, 1, 2, 5);

        assertThat(result.getList()).containsExactly(summaryBO);
        assertThat(result.getTotal()).isEqualTo(7);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
        verifyNoInteractions(summaryConvert);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteBySessionIdsRemovesSummariesOfThoseSessions() {
        when(summaryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);

        assertThat(summaryService.deleteBySessionIds(List.of("s-1"))).isEqualTo(2);

        ArgumentCaptor<LambdaQueryWrapper<SummaryDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(summaryMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("sessionId IN");
    }

    @Test
    void saveFillsDefaultsBeforeInsert() {
        SummaryBO summary = new SummaryBO();
        summary.setDeviceId("device-1");
        summary.setRoleId(1);
        summary.setLastMessageTimestamp(Instant.now());
        SummaryDO summaryDO = new SummaryDO();

        when(summaryConvert.toDO(summary)).thenReturn(summaryDO);

        summaryService.save(summary);

        ArgumentCaptor<SummaryDO> captor = ArgumentCaptor.forClass(SummaryDO.class);
        verify(summaryMapper).insert(captor.capture());
        assertThat(captor.getValue().getPromptTokens()).isZero();
        assertThat(captor.getValue().getCompletionTokens()).isZero();
        assertThat(captor.getValue().getCreateTime()).isNotNull();
    }

    // 主键是设备、角色加批次最后一条消息的时间：另一个对话实例已经存过同一批的摘要时不算失败，否则这批会反复重试到被丢弃
    @Test
    void saveTreatsADuplicateOfTheSameBatchAsAlreadySaved() {
        SummaryBO summary = new SummaryBO();
        summary.setDeviceId("device-1");
        summary.setRoleId(1);
        summary.setLastMessageTimestamp(Instant.now());
        when(summaryConvert.toDO(summary)).thenReturn(new SummaryDO());
        when(summaryMapper.insert(any(SummaryDO.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatCode(() -> summaryService.save(summary)).doesNotThrowAnyException();
    }

    @Test
    void saveSkipsInvalidSummary() {
        SummaryBO summary = new SummaryBO();
        summary.setDeviceId("device-1");

        summaryService.save(summary);

        verifyNoInteractions(summaryMapper, summaryConvert);
    }

    @Test
    void deleteReturnsZeroWhenInputInvalid() {
        assertThat(summaryService.delete(null, "device-1", null)).isZero();
        assertThat(summaryService.delete(1, " ", null)).isZero();
    }

    @Test
    void deleteUsesSummaryIdAsCreateTimeFilter() {
        long summaryId = 1710000000123L;

        when(summaryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        int result = summaryService.delete(1, "device-1", summaryId);

        assertThat(result).isEqualTo(1);

        ArgumentCaptor<LambdaQueryWrapper<SummaryDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(summaryMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql())
            .contains("roleId =")
            .contains("deviceId =")
            .contains("createTime =");
        // summaryId 是毫秒时间戳，转出的 createTime 必须能原样还原回该毫秒值
        LocalDateTime createTimeFilter = captor.getValue().getParamNameValuePairs().values().stream()
            .filter(LocalDateTime.class::isInstance)
            .map(LocalDateTime.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(createTimeFilter.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .isEqualTo(summaryId);
    }

    @Test
    void deleteOmitsCreateTimeFilterWhenSummaryIdMissing() {
        when(summaryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

        assertThat(summaryService.delete(1, "device-1", null)).isEqualTo(3);

        ArgumentCaptor<LambdaQueryWrapper<SummaryDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(summaryMapper).delete(captor.capture());
        assertThat(captor.getValue().getTargetSql()).doesNotContain("createTime");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder(1, "device-1");
    }

    @Test
    void findLastReturnsNullWhenInputInvalid() {
        assertThat(summaryService.findLast(" ", 1)).isNull();
        assertThat(summaryService.findLast("device-1", null)).isNull();
        verifyNoInteractions(summaryMapper, summaryConvert);
    }

    @Test
    void findLastReturnsConvertedSummary() {
        SummaryDO summaryDO = new SummaryDO();
        SummaryBO summaryBO = new SummaryBO();

        when(summaryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(summaryDO);
        when(summaryConvert.toBO(summaryDO)).thenReturn(summaryBO);

        SummaryBO result = summaryService.findLast("device-1", 1);

        assertThat(result).isSameAs(summaryBO);
    }
}
