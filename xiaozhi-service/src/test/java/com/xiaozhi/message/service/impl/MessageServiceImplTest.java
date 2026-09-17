package com.xiaozhi.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.bo.MessageBO;
import com.xiaozhi.common.model.PageResult;
import com.xiaozhi.event.ConversationHistoryClearedEvent;
import com.xiaozhi.message.convert.MessageConvert;
import com.xiaozhi.message.dal.mysql.dataobject.MessageDO;
import com.xiaozhi.message.dal.mysql.mapper.ConversationMapper;
import com.xiaozhi.message.dal.mysql.mapper.MessageMapper;
import com.xiaozhi.message.model.ConversationProjection;
import com.xiaozhi.message.model.MessageProjection;
import com.xiaozhi.storage.service.StorageService;
import com.xiaozhi.storage.service.StorageServiceFactory;
import com.xiaozhi.support.MybatisPlusTestHelper;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private StorageServiceFactory storageServiceFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private RuntimePathConfig runtimePathConfig;

    @InjectMocks
    private MessageServiceImpl messageService;

    // AudioUtils.AUDIO_PATH 只由 RuntimePathConfig 的 @PostConstruct 初始化，
    // 纯 Mockito 单测里这条 Spring 生命周期不会跑，需要手动布置并在用例结束后还原，
    // 避免污染同一 JVM 里其它测试类读到的静态值
    private String originalAudioPath;

    @BeforeEach
    void captureAudioPath() {
        originalAudioPath = AudioUtils.AUDIO_PATH;
    }

    @AfterEach
    void restoreAudioPath() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

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

        when(conversationMapper.selectConversationPage(any(Page.class), eq(7), eq(3), eq("web"))).thenReturn(page);
        when(conversationMapper.selectConversationCount(7, 3, "web")).thenReturn(42L);

        PageResult<ConversationProjection> result = messageService.conversationPage(1, 10, 7, 3, "web");

        assertThat(result.getList()).containsExactly(projection);
        // 总数走轻量 count 查询，不能再依赖 MyBatis-Plus 对带子查询的主查询自动 COUNT
        assertThat(result.getTotal()).isEqualTo(42);

        ArgumentCaptor<Page<ConversationProjection>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(conversationMapper).selectConversationPage(pageCaptor.capture(), eq(7), eq(3), eq("web"));
        assertThat(pageCaptor.getValue().searchCount()).isFalse();
    }

    /**
     * 短期记忆按时间窗回捞历史。排序口径一旦改回按 sender 排，
     * tool 响应会跑到触发它的 assistant 工具调用之前，provider 直接 400 拒绝整轮请求。
     */
    @Test
    void listHistoryAfterQueriesStrictlyAfterTimeOrderedByCreateTimeThenId() {
        Instant after = LocalDateTime.of(2026, 9, 5, 10, 0).atZone(ZoneId.systemDefault()).toInstant();
        MessageDO toolCall = new MessageDO();
        toolCall.setMessageId(1L);
        MessageDO toolResult = new MessageDO();
        toolResult.setMessageId(2L);
        MessageBO toolCallBO = new MessageBO();
        toolCallBO.setMessageId(1L);
        MessageBO toolResultBO = new MessageBO();
        toolResultBO.setMessageId(2L);

        // 查询取的是倒序最近 N 条，服务层再翻回正序
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(toolResult, toolCall));
        when(messageConvert.toBO(toolCall)).thenReturn(toolCallBO);
        when(messageConvert.toBO(toolResult)).thenReturn(toolResultBO);

        List<MessageBO> history = messageService.listHistoryAfter("dev-1", 3, after);

        // 喂给模型的历史必须是时间正序，工具调用要排在工具响应前面
        assertThat(history).containsExactly(toolCallBO, toolResultBO);

        ArgumentCaptor<LambdaQueryWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        LambdaQueryWrapper<MessageDO> wrapper = captor.getValue();
        String sql = wrapper.getTargetSql();

        // 严格大于：用 >= 会把上一次已经喂过的那条重复带进上下文
        assertThat(sql).contains("createTime > ?").doesNotContain("createTime >= ?");
        assertThat(sql).contains("deviceId = ?").contains("roleId = ?").contains("state = ?");
        // 倒序 + LIMIT：积压过多时保留离当前对话最近的那批，而不是最老的
        assertThat(sql).contains("ORDER BY createTime DESC,messageId DESC");
        assertThat(sql).contains("LIMIT 500");
        assertThat(sql)
            .as("按 sender 排序会打乱工具调用与工具响应的先后")
            .doesNotContain("sender");
        assertThat(wrapper.getParamNameValuePairs().values())
            .contains("dev-1", 3, MessageBO.STATE_ENABLED,
                LocalDateTime.ofInstant(after, ZoneId.systemDefault()));
    }

    @Test
    void listHistoryAfterReturnsEmptyWithoutQueryingWhenArgumentsMissing() {
        assertThat(messageService.listHistoryAfter(" ", 3, Instant.now())).isEmpty();
        assertThat(messageService.listHistoryAfter("dev-1", null, Instant.now())).isEmpty();
        assertThat(messageService.listHistoryAfter("dev-1", 3, null)).isEmpty();

        verify(messageMapper, never()).selectList(any(LambdaQueryWrapper.class));
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

        assertThatThrownBy(() -> messageService.delete(1L))
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

        messageService.delete(1L);

        verify(messageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteKeepsAudioFileWhenUpdateAffectsNoRow() {
        MessageDO messageDO = new MessageDO();
        messageDO.setMessageId(1L);
        MessageBO messageBO = new MessageBO();
        messageBO.setMessageId(1L);
        messageBO.setAudioPath("audio/2026-01-01/a.opus");

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(messageDO);
        when(messageConvert.toBO(messageDO)).thenReturn(messageBO);
        when(messageMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        try (MockedStatic<AudioUtils> audioUtils = mockStatic(AudioUtils.class)) {
            assertThatThrownBy(() -> messageService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("删除消息失败");

            audioUtils.verify(() -> AudioUtils.deleteFile(anyString()), never());
        }
    }

    @Test
    void deleteRemovesAudioFileAfterUpdateSucceeds() {
        MessageDO messageDO = new MessageDO();
        messageDO.setMessageId(1L);
        MessageBO messageBO = new MessageBO();
        messageBO.setMessageId(1L);
        messageBO.setAudioPath("audio/2026-01-01/a.opus");

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(messageDO);
        when(messageConvert.toBO(messageDO)).thenReturn(messageBO);
        when(messageMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<AudioUtils> audioUtils = mockStatic(AudioUtils.class)) {
            messageService.delete(1L);

            audioUtils.verify(() -> AudioUtils.deleteFile("audio/2026-01-01/a.opus"));
        }
    }

    @Test
    void purgeExpiredAudioRemovesFromStorageThenClearsColumn() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(messageWithAudio(1L, "audio/2026-01-01/a.opus"),
                                messageWithAudio(2L, "https://oss.example.com/audio/2026-01-01/b.opus")))
            .thenReturn(List.of());

        int purged = messageService.purgeExpiredAudio(30, 500);

        assertThat(purged).isEqualTo(2);
        // 本地路径与云端 URL 两种形态都原样交给 StorageService，由它各自解析
        // 一批里既有本地相对路径又有云地址，各自按形态路由删除
        verify(storageServiceFactory).removeFrom("audio/2026-01-01/a.opus");
        verify(storageServiceFactory).removeFrom("https://oss.example.com/audio/2026-01-01/b.opus");

        ArgumentCaptor<LambdaUpdateWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageMapper).update(isNull(), captor.capture());
        // getTargetSql 只给 WHERE，SET 子句看 getSqlSet；MP 会把 null 参数化，值在 paramNameValuePairs 里
        assertThat(captor.getValue().getSqlSet()).startsWith("audioPath=");
        assertThat(captor.getValue().getParamNameValuePairs().values()).containsNull();
        assertThat(captor.getValue().getTargetSql()).contains("messageId IN");
        // 走的是 update 不是 delete，消息行保留
        verify(messageMapper, never()).delete(any());
    }

    @Test
    void purgeExpiredAudioOnlySelectsRowsWithAudioOlderThanRetention() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        int purged = messageService.purgeExpiredAudio(30, 500);

        assertThat(purged).isZero();
        ArgumentCaptor<LambdaQueryWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("audioPath IS NOT NULL");
        assertThat(sql).contains("createTime <");
        // 没有待清理的行时不该去取存储客户端
        verifyNoInteractions(storageServiceFactory);
    }

    @Test
    void purgeExpiredAudioKeepsBatchingUntilNoRowsLeft() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(messageWithAudio(1L, "a.opus")))
            .thenReturn(List.of(messageWithAudio(2L, "b.opus")))
            .thenReturn(List.of());

        int purged = messageService.purgeExpiredAudio(30, 1);

        assertThat(purged).isEqualTo(2);
        verify(messageMapper, org.mockito.Mockito.times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void saveAllReturnsZeroForNullOrEmptyList() {
        assertThat(messageService.saveAll(null)).isZero();
        assertThat(messageService.saveAll(List.of())).isZero();
        verifyNoInteractions(messageMapper, messageConvert);
    }

    @Test
    void saveAllSkipsMetricsInsertWhenMetricsAbsentOrMessageInsertFails() {
        MessageBO withoutMetrics = new MessageBO();
        MessageDO messageDO = new MessageDO();
        when(messageConvert.toDO(withoutMetrics)).thenReturn(messageDO);
        when(messageMapper.insert(messageDO)).thenReturn(0);

        int rows = messageService.saveAll(List.of(withoutMetrics));

        assertThat(rows).isZero();
    }

    @Test
    void saveAllTruncatesOverlongTextColumnsBeforeInsert() {
        MessageBO messageBO = new MessageBO();
        MessageDO messageDO = new MessageDO();
        // 每个汉字 3 字节，4万字约12万字节，超过 text 列留出的 65000 字节安全上限
        messageDO.setMessage("字".repeat(40000));
        messageDO.setToolCalls("字".repeat(40000));
        when(messageConvert.toDO(messageBO)).thenReturn(messageDO);
        when(messageMapper.insert(messageDO)).thenReturn(1);

        messageService.saveAll(List.of(messageBO));

        assertThat(messageDO.getMessage())
            .hasSizeLessThan(40000)
            .endsWith("...(内容过长已截断)");
        assertThat(messageDO.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(65000);
        assertThat(messageDO.getToolCalls()).endsWith("...(内容过长已截断)");
    }

    @Test
    void listHistoryByDeviceReturnsEmptyWithoutQueryingWhenArgumentsMissing() {
        assertThat(messageService.listHistory(" ", 3, 10)).isEmpty();
        assertThat(messageService.listHistory("dev-1", (Integer) null, 10)).isEmpty();
        assertThat(messageService.listHistory("dev-1", 3, 0)).isEmpty();

        verify(messageMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listHistoryByDeviceQueriesRecentLimitAndReversesToChronologicalOrder() {
        MessageDO first = new MessageDO();
        first.setMessageId(1L);
        MessageDO second = new MessageDO();
        second.setMessageId(2L);
        MessageBO firstBO = new MessageBO();
        firstBO.setMessageId(1L);
        MessageBO secondBO = new MessageBO();
        secondBO.setMessageId(2L);

        // 查询取的是倒序最近 N 条，服务层再翻回正序，与 listHistoryAfter 同一口径
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(second, first));
        when(messageConvert.toBO(first)).thenReturn(firstBO);
        when(messageConvert.toBO(second)).thenReturn(secondBO);

        List<MessageBO> history = messageService.listHistory("dev-1", 3, 10);

        assertThat(history).containsExactly(firstBO, secondBO);

        ArgumentCaptor<LambdaQueryWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("deviceId = ?").contains("roleId = ?").contains("state = ?");
        assertThat(sql).contains("ORDER BY createTime DESC,messageId DESC");
        assertThat(sql).contains("LIMIT 10");
    }

    @Test
    void listHistoryBySessionReturnsEmptyWithoutQueryingWhenArgumentsMissing() {
        assertThat(messageService.listHistory(" ", 10)).isEmpty();
        assertThat(messageService.listHistory("session-1", 0)).isEmpty();

        verify(messageMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listHistoryBySessionQueriesRecentLimitAndReversesToChronologicalOrder() {
        MessageDO first = new MessageDO();
        first.setMessageId(1L);
        MessageDO second = new MessageDO();
        second.setMessageId(2L);
        MessageBO firstBO = new MessageBO();
        firstBO.setMessageId(1L);
        MessageBO secondBO = new MessageBO();
        secondBO.setMessageId(2L);

        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(second, first));
        when(messageConvert.toBO(first)).thenReturn(firstBO);
        when(messageConvert.toBO(second)).thenReturn(secondBO);

        List<MessageBO> history = messageService.listHistory("session-1", 10);

        assertThat(history).containsExactly(firstBO, secondBO);

        ArgumentCaptor<LambdaQueryWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertThat(sql).contains("sessionId = ?").contains("state = ?");
        assertThat(sql).contains("ORDER BY createTime DESC,messageId DESC");
        assertThat(sql).contains("LIMIT 10");
    }

    @Test
    void updateAssistantAudioReturnsEarlyWhenRequiredArgsMissing() {
        messageService.updateAssistantAudio(" ", 1, LocalDateTime.now(), "a.opus", BigDecimal.ONE);
        messageService.updateAssistantAudio("dev-1", null, LocalDateTime.now(), "a.opus", BigDecimal.ONE);
        messageService.updateAssistantAudio("dev-1", 1, null, "a.opus", BigDecimal.ONE);

        verifyNoInteractions(messageMapper);
    }

    @Test
    void updateAssistantAudioDoesNothingWhenAssistantMessageNotFound() {
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        messageService.updateAssistantAudio("dev-1", 1, LocalDateTime.now(), "a.opus", BigDecimal.ONE);

        verify(messageMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateAssistantAudioSkipsMetricsUpdateWhenTtsDurationNull() {
        MessageDO found = new MessageDO();
        found.setMessageId(9L);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(found);

        messageService.updateAssistantAudio("dev-1", 1, LocalDateTime.now(), "a.opus", null);

        verify(messageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void truncateAssistantReturnsEarlyWhenRequiredArgsMissing() {
        messageService.truncateAssistant(" ", 1, LocalDateTime.now(), "text");
        messageService.truncateAssistant("dev-1", null, LocalDateTime.now(), "text");
        messageService.truncateAssistant("dev-1", 1, null, "text");

        verifyNoInteractions(messageMapper);
    }

    @Test
    void truncateAssistantDoesNothingWhenMessageNotFound() {
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        messageService.truncateAssistant("dev-1", 1, LocalDateTime.now(), "text");

        verify(messageMapper, never()).deleteById(any());
        verify(messageMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void truncateAssistantDeletesMessageWhenSpokenTextBlank() {
        MessageDO found = new MessageDO();
        found.setMessageId(9L);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(found);

        messageService.truncateAssistant("dev-1", 1, LocalDateTime.now(), " ");

        verify(messageMapper).deleteById(9L);
        verify(messageMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        // metrics 行交给外键 ON DELETE CASCADE，不再手工删一次
    }

    @Test
    void truncateAssistantUpdatesMessageTextWhenSpokenTextProvided() {
        MessageDO found = new MessageDO();
        found.setMessageId(9L);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(found);

        messageService.truncateAssistant("dev-1", 1, LocalDateTime.now(), "打断前已说的部分");

        ArgumentCaptor<LambdaUpdateWrapper<MessageDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("message=");
        verify(messageMapper, never()).deleteById(any());
    }

    @Test
    void deleteByDeviceIdReturnsZeroWithoutSideEffectsWhenDeviceIdBlank() {
        assertThat(messageService.deleteByDeviceId(" ")).isZero();
    }

    @Test
    void deleteByDeviceIdMarksMessagesDeletedAndPublishesClearedEvent() {
        AudioUtils.AUDIO_PATH = "audio/";
        when(messageMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(3);
        when(runtimePathConfig.resolveStorageKey(anyString()))
            .thenReturn(Path.of("build/tmp/p3-557-does-not-exist"));

        int updated = messageService.deleteByDeviceId("AA:BB:CC");

        assertThat(updated).isEqualTo(3);

        ArgumentCaptor<LambdaUpdateWrapper<MessageDO>> updateCaptor =
            ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getTargetSql()).contains("deviceId = ?").contains("state = ?");

        // 保留期内每天一个目录（含今天）；deviceId 里的冒号要换成短横线才是合法路径片段
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(runtimePathConfig, times(AudioUtils.AUDIO_RETENTION_DAYS + 1)).resolveStorageKey(keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).allSatisfy(key ->
            assertThat(key).contains("AA-BB-CC").doesNotContain(":"));

        ArgumentCaptor<ConversationHistoryClearedEvent> eventCaptor =
            ArgumentCaptor.forClass(ConversationHistoryClearedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDeviceId()).isEqualTo("AA:BB:CC");
    }

    private static MessageDO messageWithAudio(Long messageId, String audioPath) {
        MessageDO message = new MessageDO();
        message.setMessageId(messageId);
        message.setAudioPath(audioPath);
        return message;
    }
}
