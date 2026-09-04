package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.dal.mysql.mapper.ConversationMapper;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.message.model.ConversationProjection;
import com.xiaozhi.message.model.MessageProjection;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(MessageDO.class);
    }

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageConvert messageConvert;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void pageReturnsProjectionRecordsUntouched() {
        MessageProjection projection = new MessageProjection();
        projection.setMessageId(1L);
        projection.setDeviceName("客厅音箱");

        Page<MessageProjection> page = new Page<>(2, 5);
        page.setRecords(List.of(projection));
        page.setTotal(8);

        when(messageMapper.selectPage(any(Page.class), eq("dev-1"), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), eq(7), isNull(), isNull())).thenReturn(page);

        PageResult<MessageProjection> result = messageService.page(2, 5, "dev-1", null, null, null, null,
            null, null, 7, null, null);

        assertThat(result.getList()).containsExactly(projection);
        assertThat(result.getTotal()).isEqualTo(8);
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
    }

    @Test
    void conversationPageReturnsProjectionRecordsUntouched() {
        ConversationProjection projection = new ConversationProjection();
        projection.setSessionId("s-1");
        projection.setTitle("今天天气怎么样");

        Page<ConversationProjection> page = new Page<>(1, 10);
        page.setRecords(List.of(projection));
        page.setTotal(1);

        when(conversationMapper.selectConversationPage(any(Page.class), eq(7), eq(3), eq("web"))).thenReturn(page);

        PageResult<ConversationProjection> result = messageService.conversationPage(1, 10, 7, 3, "web");

        assertThat(result.getList()).containsExactly(projection);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void deleteThrowsWhenMessageIdMissing() {
        assertThatThrownBy(() -> messageService.delete(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("消息ID不能为空");
    }

    @Test
    void deleteThrowsWhenMessageMissing() {
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> messageService.delete(1))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("消息不存在或已删除");

        verify(messageMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void deleteMarksMessageAsDeletedWhenMessageExists() {
        MessageDO messageDO = new MessageDO();
        messageDO.setMessageId(1L);
        var messageBO = new com.xiaozhi.common.model.bo.MessageBO();
        messageBO.setMessageId(1L);
        messageBO.setAudioPath("");

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(messageDO);
        when(messageConvert.toBO(messageDO)).thenReturn(messageBO);
        when(messageMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        messageService.delete(1);

        verify(messageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoInteractions(eventPublisher);
    }
}
