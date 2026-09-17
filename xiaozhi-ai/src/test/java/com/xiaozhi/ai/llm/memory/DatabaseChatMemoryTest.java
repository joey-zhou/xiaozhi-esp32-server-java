package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Web 会话的摘要挂在会话上：压缩线程调模型的这几秒里用户可能已经把会话删了，落库前要确认会话还在；
 * 设备端摘要不挂会话，不查会话表。
 */
@ExtendWith(MockitoExtension.class)
class DatabaseChatMemoryTest {

    @Mock
    private MessageService messageService;
    @Mock
    private SummaryService summaryService;
    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private DatabaseChatMemory chatMemory;

    @Test
    void summaryOfADeletedWebConversationIsDropped() {
        when(conversationService.get("s-1")).thenReturn(null);

        chatMemory.save(new SummaryBO().setDeviceId("web:9").setRoleId(1).setSessionId("s-1").setSummary("摘要"));

        verify(summaryService, never()).save(any());
    }

    @Test
    void summaryOfAnExistingWebConversationIsSaved() {
        when(conversationService.get("s-1")).thenReturn(new ConversationBO());
        SummaryBO summary = new SummaryBO().setDeviceId("web:9").setRoleId(1).setSessionId("s-1").setSummary("摘要");

        chatMemory.save(summary);

        verify(summaryService).save(summary);
    }

    @Test
    void deviceSummaryIsSavedWithoutConsultingTheConversationTable() {
        SummaryBO summary = new SummaryBO().setDeviceId("device-1").setRoleId(1).setSummary("摘要");

        chatMemory.save(summary);

        verify(summaryService).save(summary);
        verifyNoInteractions(conversationService);
    }
}
