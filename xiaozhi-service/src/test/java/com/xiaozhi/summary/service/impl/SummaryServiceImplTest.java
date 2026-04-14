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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void pageReturnsDefaultEmptyResponseWhenInputInvalid() {
        var result = summaryService.page(" ", null, null, null);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        verifyNoInteractions(summaryMapper, summaryConvert);
    }

    @Test
    void pageReturnsConvertedRecordsWhenInputValid() {
        SummaryDO summaryDO = new SummaryDO();
        SummaryBO summaryBO = new SummaryBO();

        Page<SummaryDO> page = new Page<>(2, 5);
        page.setRecords(List.of(summaryDO));
        page.setTotal(7);

        when(summaryMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(summaryConvert.toBOList(List.of(summaryDO))).thenReturn(List.of(summaryBO));

        var result = summaryService.page("device-1", 1, 2, 5);

        assertThat(result.getList()).containsExactly(summaryBO);
        assertThat(result.getTotal()).isEqualTo(7);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
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
        verify(summaryMapper).delete(any(LambdaQueryWrapper.class));
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
