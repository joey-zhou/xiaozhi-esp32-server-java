package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private MessageConvert messageConvert;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

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
