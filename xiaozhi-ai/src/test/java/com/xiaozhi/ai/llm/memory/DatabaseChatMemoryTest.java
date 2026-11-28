package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.message.service.ConversationService;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 摘要落库交给会话服务，会话已删除时丢弃的判断在那里 */
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
    void summariesGoThroughTheConversationService() {
        SummaryBO summary = new SummaryBO().setDeviceId("web:9").setRoleId(1).setSessionId("s-1").setSummary("摘要");

        chatMemory.save(summary);

        verify(conversationService).saveSummary(summary);
        verifyNoInteractions(summaryService);
    }
}
