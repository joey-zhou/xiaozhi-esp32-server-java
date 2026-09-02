package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.SummaryBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住批量摘要的触发条件、互斥与收尾。摘要跑在虚拟线程里、失败只打日志，
 * 一旦 summarizing 卡在 true 该会话此后永不摘要、上下文无限增长到超 token；
 * 而摘要成功后按 equals 删消息会把内容相同的另一条历史一起删掉，线上只表现为「机器人忘事」。
 * 这两条都无法从外部观测，只能靠本类钉住。
 */
class SummaryConversationTest {

    private static final String OWNER = "device-1";
    private static final int ROLE_ID = 1;
    private static final long AWAIT_TIMEOUT_MS = 5000;
    private static final long QUIET_WINDOW_MS = 300;

    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final ChatModel chatModel = mock(ChatModel.class);

    private SummaryConversation conversation(int maxMessages, int batchSize) {
        return SummaryConversation.builder()
                .ownerId(OWNER)
                .roleId(ROLE_ID)
                .sessionId("session-1")
                .roleDesc("测试角色")
                .userId(1)
                .initSummarizerPromptTemplate(template("首次摘要 $datetime$" + System.lineSeparator() + "$conversation$"))
                .againSummarizerPromptTemplate(template("续写摘要 $last_summary$" + System.lineSeparator() + "$conversation$"))
                .chatMemory(chatMemory)
                .chatModel(chatModel)
                .maxMessages(maxMessages)
                .batchSize(batchSize)
                .build();
    }

    /** 与 SummaryConversationFactory 一致：$ 作为占位符定界符 */
    private static PromptTemplate template(String text) {
        return PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
                .template(text)
                .build();
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static Message timestamped(Message message, Instant instant) {
        MessageTimeMetadata.setTimeMillis(message, instant);
        return message;
    }

    /** 摘要与角色系统提示词都是 SystemMessage，剔掉后只留真实对话历史的文本 */
    private static List<String> historyTexts(SummaryConversation conversation) {
        return conversation.messages().stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .map(Message::getText)
                .toList();
    }

    private static List<String> systemTexts(SummaryConversation conversation) {
        return conversation.messages().stream()
                .filter(message -> message.getMessageType() == MessageType.SYSTEM)
                .map(Message::getText)
                .toList();
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    // 摘要只移除本批消息，按对象身份匹配：用户连说两次「嗯」时，
    // 后面那条内容相同的消息不能跟着首批一起消失，否则它既不在内存也没进摘要
    @Test
    void summaryOnlyRemovesItsOwnBatchNotLaterEqualMessages() throws InterruptedException {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("用户在闲聊"));
        SummaryConversation conversation = conversation(4, 2);

        conversation.add(new UserMessage("嗯"));
        conversation.add(new AssistantMessage("我在"));
        conversation.add(new UserMessage("嗯"));
        conversation.add(new AssistantMessage("好的"));

        awaitUntil(() -> historyTexts(conversation).size() == 2);
        assertThat(historyTexts(conversation)).containsExactly("嗯", "好的");
        verify(chatMemory).save(any(SummaryBO.class));
    }

    @Test
    void summaryIsNotTriggeredBeforeMaxMessagesIsReached() {
        SummaryConversation conversation = conversation(6, 2);

        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好呀"));

        verify(chatModel, after(QUIET_WINDOW_MS).never()).call(any(Prompt.class));
        assertThat(historyTexts(conversation)).containsExactly("你好", "你好呀");
    }

    // 摘要抛异常时消息必须原样留在内存里，summarizing 必须复位，否则该会话此后再也不会摘要
    @Test
    void summarizeFailureKeepsMessagesAndReleasesSummarizingFlag() throws InterruptedException {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("摘要模型不可用"));
        SummaryConversation conversation = conversation(2, 2);

        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好呀"));

        verify(chatModel, timeout(AWAIT_TIMEOUT_MS)).call(any(Prompt.class));
        awaitUntil(() -> Boolean.FALSE.equals(ReflectionTestUtils.getField(conversation, "summarizing")));
        assertThat(historyTexts(conversation)).containsExactly("你好", "你好呀");
        verify(chatMemory, never()).save(any(SummaryBO.class));

        // 复位后下一条助手消息要能重新拉起摘要
        conversation.add(new AssistantMessage("还在吗"));
        verify(chatModel, timeout(AWAIT_TIMEOUT_MS).times(2)).call(any(Prompt.class));
    }

    // 上一轮摘要还在跑时新消息不能再起一轮；等它跑完，堆积的消息由收尾处的递归 summarize() 接着摘
    @Test
    void secondSummaryWaitsUntilTheRunningOneFinishes() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return reply("用户在闲聊");
        });
        SummaryConversation conversation = conversation(2, 2);

        conversation.add(new UserMessage("第一问"));
        conversation.add(new AssistantMessage("第一答"));
        assertThat(entered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        // 首轮摘要仍被阻塞，此时再攒够一批也不能起第二个线程
        conversation.add(new UserMessage("第二问"));
        conversation.add(new AssistantMessage("第二答"));
        verify(chatModel, times(1)).call(any(Prompt.class));

        release.countDown();
        verify(chatModel, timeout(AWAIT_TIMEOUT_MS).times(2)).call(any(Prompt.class));
        awaitUntil(() -> historyTexts(conversation).isEmpty());
    }

    // 最后一条消息距今超过 1 小时且够 2 条，构造时就要 force 一次摘要把上下文压掉
    @Test
    void staleHistoryTriggersForcedSummaryOnConstruction() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        when(chatMemory.find(OWNER, ROLE_ID, 4)).thenReturn(List.of(
                timestamped(new UserMessage("昨天聊到哪了"), twoHoursAgo),
                timestamped(new AssistantMessage("聊到篮球"), twoHoursAgo)));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("用户喜欢篮球"));

        conversation(4, 2);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, timeout(AWAIT_TIMEOUT_MS)).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
                .contains("首次摘要")
                .contains("昨天聊到哪了");
        verify(chatMemory, timeout(AWAIT_TIMEOUT_MS)).save(any(SummaryBO.class));
    }

    @Test
    void freshHistoryIsLoadedWithoutSummaryOnConstruction() {
        when(chatMemory.find(OWNER, ROLE_ID, 4)).thenReturn(List.of(
                timestamped(new UserMessage("刚才说到哪了"), Instant.now()),
                timestamped(new AssistantMessage("说到篮球"), Instant.now())));

        SummaryConversation conversation = conversation(4, 2);

        verify(chatModel, after(QUIET_WINDOW_MS).never()).call(any(Prompt.class));
        assertThat(historyTexts(conversation)).hasSize(2);
    }

    // 已有 summary 时按 summary 的时间戳只加载未被摘要的尾巴，并且续写摘要要把上一版摘要带进提示词
    @Test
    void existingSummaryLoadsOnlyUnsummarizedTailAndReusesItInPrompt() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        SummaryBO lastSummary = new SummaryBO()
                .setDeviceId(OWNER)
                .setRoleId(ROLE_ID)
                .setSummary("上次聊到用户喜欢篮球")
                .setLastMessageTimestamp(twoHoursAgo)
                .setCreateTime(twoHoursAgo);
        when(chatMemory.findLastSummary(OWNER, ROLE_ID)).thenReturn(lastSummary);
        when(chatMemory.find(eq(OWNER), eq(ROLE_ID), any(Instant.class))).thenReturn(List.of(
                new UserMessage("接着聊"), new AssistantMessage("好呀")));
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("摘要模型不可用"));

        SummaryConversation conversation = conversation(4, 2);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, timeout(AWAIT_TIMEOUT_MS)).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
                .contains("续写摘要")
                .contains("上次聊到用户喜欢篮球")
                .contains("接着聊");
        assertThat(historyTexts(conversation)).containsExactly("接着聊", "好呀");
        assertThat(systemTexts(conversation)).anySatisfy(text ->
                assertThat(text).contains("上次聊到用户喜欢篮球"));
        verify(chatMemory, never()).find(OWNER, ROLE_ID, 4);
    }
}
