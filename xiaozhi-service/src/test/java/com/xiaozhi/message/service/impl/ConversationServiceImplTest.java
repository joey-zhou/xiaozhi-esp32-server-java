package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.common.model.bo.ConversationBO;
import com.xiaozhi.message.convert.MessageConvertImpl;
import com.xiaozhi.message.dal.mysql.dataobject.ConversationDO;
import com.xiaozhi.message.dal.mysql.mapper.ConversationMapper;
import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.summary.service.SummaryService;
import com.xiaozhi.support.MybatisPlusTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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

/**
 * 会话按用户隔离：列表、改名、删除都只动当前用户名下的会话，删除连带清掉聊天记录与摘要；自动标题取第一句话的开头。
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo(ConversationDO.class);
    }

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private SummaryService summaryService;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationMapper, new MessageConvertImpl(), messageService, summaryService);
    }

    private static ConversationDO conversation(String sessionId) {
        ConversationDO conversation = new ConversationDO();
        conversation.setSessionId(sessionId);
        conversation.setUserId(7);
        conversation.setRoleId(3);
        conversation.setTitle("今天天气怎么样");
        return conversation;
    }

    @Test
    void createTakesTheOpeningOfTheFirstMessageAsTitle() {
        service.create("s-1", 7, 3, "  帮我\n规划一下" + "周末".repeat(30) + "  ");

        ArgumentCaptor<ConversationDO> inserted = ArgumentCaptor.forClass(ConversationDO.class);
        verify(conversationMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTitle())
            .startsWith("帮我 规划一下周末")
            .hasSize(ConversationServiceImpl.AUTO_TITLE_LENGTH);
        assertThat(inserted.getValue().getUserId()).isEqualTo(7);
        assertThat(inserted.getValue().getUpdateTime()).isNotNull();
    }

    // 同一会话已经建过档，撞主键不能让这一轮对话失败
    @Test
    void createKeepsTheExistingConversationOnDuplicate() {
        when(conversationMapper.insert(any(ConversationDO.class))).thenThrow(new DuplicateKeyException("dup"));

        service.create("s-1", 7, 3, "你好");
    }

    // 分页由 XML 的 JOIN 直出 BO（含角色名），Service 只透传当前用户与角色条件
    @Test
    void pageOnlyReadsTheCurrentUsersConversations() {
        ConversationBO conversation = new ConversationBO();
        conversation.setSessionId("s-1");
        conversation.setRoleName("小智");
        Page<ConversationBO> page = new Page<>(1, 10);
        page.setRecords(List.of(conversation));
        page.setTotal(1);
        when(conversationMapper.selectPage(any(Page.class), eq(7), isNull())).thenReturn(page);

        PageResult<ConversationBO> result = service.page(7, null, 1, 10);

        assertThat(result.getList()).containsExactly(conversation);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    // 传进来的会话里混了别人的，只删查得到归属的那部分
    @Test
    void deleteOnlyRemovesOwnedConversationsWithTheirMessagesAndSummaries() {
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(conversation("s-1")));

        List<String> deleted = service.delete(7, List.of("s-1", "someone-else"));

        assertThat(deleted).containsExactly("s-1");
        verify(conversationMapper).delete(any(LambdaQueryWrapper.class));
        verify(messageService).deleteBySessionIds(List.of("s-1"));
        verify(summaryService).deleteBySessionIds(List.of("s-1"));
    }

    @Test
    void deleteWithoutOwnedConversationsTouchesNothing() {
        when(conversationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.delete(7, List.of("someone-else"))).isEmpty();

        verify(conversationMapper, never()).delete(any(LambdaQueryWrapper.class));
        verifyNoInteractions(messageService, summaryService);
    }

    @SuppressWarnings("unchecked")
    @Test
    void renamingAConversationThatIsNotOwnedIsNotFound() {
        when(conversationMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rename(7, "s-1", "周末计划"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
