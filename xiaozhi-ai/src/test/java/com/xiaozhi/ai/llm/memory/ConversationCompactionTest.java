package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.ai.llm.TokenEstimator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住对话压缩的触发、互斥与收尾。压缩跑在虚拟线程里、失败只打日志：compacting 卡在 true 时该对话此后永不压缩、
 * 上下文无限增长；按 equals 删消息会把内容相同的另一条历史一起删掉，线上只表现为「机器人忘事」。
 * 摘要成功才移出上下文。
 */
class ConversationCompactionTest {

    private static final long AWAIT_TIMEOUT_MS = 5000;
    private static final long QUIET_WINDOW_MS = 300;

    private final Summarizer summarizer = mock(Summarizer.class);

    /** 条数触发、压到只剩 keep 条；token 预算给得很大，不参与 */
    private Conversation conversation(int maxMessages, int keepMessages) {
        return conversation(maxMessages, keepMessages, 100_000, List.of());
    }

    private Conversation conversation(int maxMessages, int keepMessages, int tokenBudget, List<Message> history) {
        return Conversation.builder()
                .ownerId("device-1")
                .roleId(1)
                .sessionId("session-1")
                .roleDesc("测试角色")
                .userId(1)
                .history(history)
                .summarizer(summarizer)
                .maxMessages(maxMessages)
                .tokenBudget(tokenBudget)
                .promptOverhead(0)
                .keepMessages(keepMessages)
                .build();
    }

    private static AssistantMessage replyWithUsage(String text, int promptTokens) {
        return AssistantMessage.builder()
                .content(text)
                .properties(Map.of(ChatMemory.USAGE_KEY, new DefaultUsage(promptTokens, 20)))
                .build();
    }

    private static List<String> texts(Conversation conversation) {
        synchronized (conversation) {
            return new ArrayList<>(conversation.rawMessages()).stream().map(Message::getText).toList();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static AssistantMessage toolCall(String callId, String name) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", name, "{}")))
                .build();
    }

    private static ToolResponseMessage toolResponse(String callId, String name) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(callId, name, "晴")))
                .build();
    }

    private static <T extends Message> T at(T message, Instant instant) {
        MessageTimeMetadata.setTimeMillis(message, instant);
        return message;
    }

    // 只移除本批，按对象身份匹配：用户连说两次「嗯」时后面那条不能跟着消失；摘要随后出现在系统消息里
    @Test
    void compactionRemovesOnlyItsOwnBatchAndKeepsTheSummary() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户在闲聊");
        Conversation conversation = conversation(4, 2);
        UserMessage first = new UserMessage("嗯");
        AssistantMessage firstReply = new AssistantMessage("我在");

        conversation.add(first);
        conversation.add(firstReply);
        conversation.add(new UserMessage("嗯"));
        conversation.add(new AssistantMessage("好的"));

        awaitUntil(() -> texts(conversation).size() == 2);
        assertThat(texts(conversation)).containsExactly("嗯", "好的");
        verify(summarizer).summarize(null, List.of(first, firstReply));
        assertThat(conversation.messages())
                .filteredOn(message -> message.getMessageType() == MessageType.SYSTEM)
                .extracting(Message::getText)
                .anySatisfy(text -> assertThat(text).contains("用户在闲聊"));
    }

    @Test
    void compactionIsNotTriggeredBeforeAnyLimitIsReached() {
        Conversation conversation = conversation(6, 2);

        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好呀"));

        verify(summarizer, after(QUIET_WINDOW_MS).never()).summarize(nullable(String.class), anyList());
        assertThat(texts(conversation)).containsExactly("你好", "你好呀");
    }

    // 条数没到上限，但模型返回的输入 token 已经到预算（工具返回这类长内容），也要压缩；
    // 只剩最近几条时至少压一组，不能因为 keep 而什么都不压
    @Test
    void promptTokensOverBudgetTriggerCompactionBeforeMaxMessages() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户问了天气");
        Conversation conversation = conversation(20, 8, 1000, List.of());

        conversation.add(new UserMessage("今天天气怎么样"));
        conversation.add(replyWithUsage("今天晴", 1200));

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 不带工具的一轮，模型回传的输入 token 就是上下文大小；这条回复本身不在那次用量里，要加上它的估算
    @Test
    void trustedUsageBecomesTheBaselineAndLaterMessagesAreEstimatedOnTop() {
        Conversation conversation = conversation(20, 8, 100_000, List.of());
        conversation.add(new UserMessage("你好"));
        AssistantMessage reply = replyWithUsage("你好呀，今天想聊点什么", 2000);

        conversation.add(reply);

        assertThat(conversation.contextTokens()).isEqualTo(2000 + TokenEstimator.estimate(reply));
        UserMessage next = new UserMessage("讲个笑话");
        conversation.add(next);
        assertThat(conversation.contextTokens())
                .isEqualTo(2000 + TokenEstimator.estimate(reply) + TokenEstimator.estimate(next));
    }

    // 工具调用轮次 Spring AI 把两次调用的用量累加，数字接近上下文的两倍：不能拿它当基准，否则会提前压缩
    @Test
    void cumulativeUsageFromAToolTurnIsIgnored() {
        Conversation conversation = conversation(20, 8, 100_000, List.of());
        conversation.add(new UserMessage("你好"));
        conversation.add(replyWithUsage("你好呀", 2000));
        int before = conversation.contextTokens();

        conversation.add(new UserMessage("今天天气怎么样"));
        AssistantMessage call = toolCall("call-1", "getWeather");
        ToolResponseMessage response = toolResponse("call-1", "getWeather");
        conversation.add(call);
        conversation.add(response);
        AssistantMessage reply = replyWithUsage("今天晴", 4300);
        conversation.add(reply);

        int expected = before + TokenEstimator.estimate(List.of(new UserMessage("今天天气怎么样"), call, response, reply));
        assertThat(conversation.contextTokens()).isEqualTo(expected).isLessThan(4300);
    }

    // 下一轮不带工具的用量又会被采纳：比估算低的数字永远可信
    @Test
    void nextPlainUsageReplacesTheEstimate() {
        Conversation conversation = conversation(20, 8, 100_000, List.of());
        conversation.add(new UserMessage("你好"));
        conversation.add(replyWithUsage("你好呀", 2000));
        conversation.add(new UserMessage("再见"));
        AssistantMessage reply = replyWithUsage("再见", 1900);

        conversation.add(reply);

        assertThat(conversation.contextTokens()).isEqualTo(1900 + TokenEstimator.estimate(reply));
    }

    // 厂商不回传用量时全程估算，系统提示词这些看不见的部分按固定余量计
    @Test
    void withoutUsageTheWholeWindowIsEstimatedPlusOverhead() {
        Conversation conversation = Conversation.builder()
                .ownerId("device-1").roleId(1).sessionId("session-1").userId(1)
                .summary("用户喜欢篮球")
                .summarizer(summarizer).maxMessages(20).tokenBudget(100_000).promptOverhead(4000).keepMessages(8)
                .build();
        UserMessage user = new UserMessage("你好");
        AssistantMessage reply = new AssistantMessage("你好呀");

        conversation.add(user);
        conversation.add(reply);

        assertThat(conversation.contextTokens())
                .isEqualTo(4000 + TokenEstimator.estimate("用户喜欢篮球") + TokenEstimator.estimate(List.of(user, reply)));
    }

    // 压缩后基准还在上下文里：压掉的那批从基准里扣掉，而不是等下一轮用量前只看条数
    @Test
    void compactionSubtractsTheRemovedBatchFromTheBaseline() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户在闲聊");
        Conversation conversation = conversation(4, 2, 100_000, List.of());
        UserMessage first = new UserMessage("第一问");
        AssistantMessage firstReply = new AssistantMessage("第一答");
        conversation.add(first);
        conversation.add(firstReply);
        conversation.add(new UserMessage("第二问"));
        AssistantMessage reply = replyWithUsage("第二答", 3000);

        conversation.add(reply);

        awaitUntil(() -> texts(conversation).size() == 2);
        assertThat(conversation.contextTokens())
                .isEqualTo(3000 - TokenEstimator.estimate(List.of(first, firstReply)) + TokenEstimator.estimate(reply));
    }

    // 打断截断把助手消息换成截断版，基准跟着换，不然下一轮就退回全量估算
    @Test
    void replacingTheBaselineMessageKeepsTheBaseline() {
        Conversation conversation = conversation(20, 8, 100_000, List.of());
        conversation.add(new UserMessage("讲个故事"));
        AssistantMessage full = replyWithUsage("从前有座山，山里有座庙", 2000);
        conversation.add(full);
        AssistantMessage truncated = new AssistantMessage("从前有座山");

        conversation.replace(full, truncated);

        assertThat(conversation.contextTokens()).isEqualTo(2000 + TokenEstimator.estimate(truncated));
    }

    // 摘要失败时消息原样留在上下文，compacting 必须复位，否则此后再也不压缩
    @Test
    void summaryFailureKeepsMessages() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenThrow(new IllegalStateException("摘要模型不可用"));
        Conversation conversation = conversation(2, 0);

        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好呀"));

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> Boolean.FALSE.equals(ReflectionTestUtils.getField(conversation, "compacting")));
        assertThat(texts(conversation)).containsExactly("你好", "你好呀");
        // 复位后下一条助手消息要能重新拉起压缩
        conversation.add(new AssistantMessage("还在吗"));
        verify(summarizer, timeout(AWAIT_TIMEOUT_MS).times(2)).summarize(nullable(String.class), anyList());
    }

    // 摘要抛的是 Error（比如依赖类初始化失败）时 catch 接不住，compacting 也必须复位，下一次还能拉起压缩
    @Test
    void summaryErrorStillResetsCompacting() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList()))
            .thenThrow(new NoClassDefFoundError("AbstractEmbeddingModel"))
            .thenReturn("用户在闲聊");
        Conversation conversation = conversation(2, 0);

        conversation.add(new UserMessage("你好"));
        conversation.add(new AssistantMessage("你好呀"));

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> Boolean.FALSE.equals(ReflectionTestUtils.getField(conversation, "compacting")));
        assertThat(texts(conversation)).containsExactly("你好", "你好呀");

        conversation.add(new AssistantMessage("还在吗"));
        verify(summarizer, timeout(AWAIT_TIMEOUT_MS).times(2)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 上一轮压缩还在跑时不能再起一轮；跑完后堆积的消息由收尾处的递归接着压
    @Test
    void secondCompactionWaitsUntilTheRunningOneFinishes() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(summarizer.summarize(nullable(String.class), anyList())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return "用户在闲聊";
        });
        Conversation conversation = conversation(2, 0);

        conversation.add(new UserMessage("第一问"));
        conversation.add(new AssistantMessage("第一答"));
        assertThat(entered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        conversation.add(new UserMessage("第二问"));
        conversation.add(new AssistantMessage("第二答"));
        verify(summarizer, times(1)).summarize(nullable(String.class), anyList());

        release.countDown();
        verify(summarizer, timeout(AWAIT_TIMEOUT_MS).times(2)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 最后一条历史距今超过 1 小时且够 2 条，上一段对话已经结束，建对话时整段压缩
    @Test
    void staleHistoryIsCompactedOnConstruction() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        List<Message> history = List.of(
                at(new UserMessage("昨天聊到哪了"), twoHoursAgo),
                at(new AssistantMessage("聊到篮球"), twoHoursAgo));
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户喜欢篮球");

        conversation(4, 2, 100_000, history);

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(null, history);
    }

    @Test
    void freshHistoryIsLoadedWithoutCompaction() {
        Conversation conversation = conversation(4, 2, 100_000, List.of(
                at(new UserMessage("刚才说到哪了"), Instant.now()),
                at(new AssistantMessage("说到篮球"), Instant.now())));

        verify(summarizer, after(QUIET_WINDOW_MS).never()).summarize(nullable(String.class), anyList());
        assertThat(texts(conversation)).containsExactly("刚才说到哪了", "说到篮球");
    }

    // 批次不能切在工具链中间：切走 [User, Assistant(tool_call)] 后历史以孤儿 ToolResponseMessage 开头，
    // 下一次请求会被 OpenAI / DeepSeek / 通义直接 400
    @Test
    void batchNeverSplitsAToolChain() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户问了天气");
        Conversation conversation = conversation(2, 0);

        conversation.add(new UserMessage("今天天气怎么样"));
        conversation.add(toolCall("call-1", "getWeather"));
        verify(summarizer, after(QUIET_WINDOW_MS).never()).summarize(nullable(String.class), anyList());

        conversation.add(toolResponse("call-1", "getWeather"));
        conversation.add(new AssistantMessage("今天晴"));

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(nullable(String.class), anyList());
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 按条数取最后 N 条可能正好从工具链中间开始，带孤儿 ToolResponseMessage 的历史会被 provider 直接拒绝
    @Test
    void historyStartingInsideAToolChainDropsTheOrphanPrefix() {
        UserMessage user = new UserMessage("再讲一个");
        AssistantMessage reply = new AssistantMessage("好的");

        Conversation conversation = conversation(4, 2, 100_000, List.of(
                toolResponse("call-1", "getWeather"), new AssistantMessage("今天晴"), user, reply));

        assertThat(conversation.rawMessages()).containsExactly(user, reply);
    }

    // 会话结束时没到上限的剩余对话也要一次压完，否则短会话永远进不了摘要
    @Test
    void flushCompactsEverythingRemaining() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenReturn("用户在闲聊");
        Conversation conversation = conversation(16, 2);
        List<Message> all = List.of(new UserMessage("第一问"), new AssistantMessage("第一答"),
                new UserMessage("第二问"), new AssistantMessage("第二答"));
        all.forEach(conversation::add);

        conversation.flush();

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize(null, all);
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 压缩进行中收到 flush 不能丢：等这批结束后接着把剩下的全压掉，否则关会话时最后几轮永远进不了摘要
    @Test
    void flushDuringARunningCompactionIsHonouredAfterwards() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(summarizer.summarize(nullable(String.class), anyList())).thenAnswer(invocation -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            return "用户在闲聊";
        });
        Conversation conversation = conversation(16, 2);
        conversation.add(new UserMessage("第一问"));
        conversation.add(new AssistantMessage("第一答"));
        conversation.flush();
        assertThat(entered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        UserMessage lastQuestion = new UserMessage("第二问");
        AssistantMessage lastAnswer = new AssistantMessage("第二答");
        conversation.add(lastQuestion);
        conversation.add(lastAnswer);
        conversation.flush();
        release.countDown();

        verify(summarizer, timeout(AWAIT_TIMEOUT_MS)).summarize("用户在闲聊", List.of(lastQuestion, lastAnswer));
        awaitUntil(() -> texts(conversation).isEmpty());
    }

    // 摘要连续失败进入退避后，退避期间新消息照样堆进上下文，必须裁到硬上限，否则半小时里上下文只增不减
    @Test
    void backoffStillTrimsTheContextToTheHardCap() throws InterruptedException {
        when(summarizer.summarize(nullable(String.class), anyList())).thenThrow(new IllegalStateException("摘要模型不可用"));
        Conversation conversation = conversation(2, 0);
        for (int i = 0; i < 2; i++) {
            conversation.add(new UserMessage("问" + i));
            conversation.add(new AssistantMessage("答" + i));
            int attempts = i + 1;
            verify(summarizer, timeout(AWAIT_TIMEOUT_MS).times(attempts)).summarize(nullable(String.class), anyList());
            awaitUntil(() -> Integer.valueOf(attempts).equals(ReflectionTestUtils.getField(conversation, "consecutiveFailures")));
        }

        for (int i = 2; i < 12; i++) {
            conversation.add(new UserMessage("问" + i));
            conversation.add(new AssistantMessage("答" + i));
        }

        // 硬上限是 maxMessages 的 4 倍
        assertThat(texts(conversation)).hasSizeLessThanOrEqualTo(8).endsWith("问11", "答11");
        verify(summarizer, times(2)).summarize(nullable(String.class), anyList());
    }

    // 会话被删除后不再开始新的压缩
    @Test
    void discardStopsFurtherCompaction() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(summarizer.summarize(nullable(String.class), anyList())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return "用户在闲聊";
        });
        Conversation conversation = conversation(2, 0);
        conversation.add(new UserMessage("第一问"));
        conversation.add(new AssistantMessage("第一答"));
        assertThat(entered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        conversation.discard();
        release.countDown();
        awaitUntil(() -> Boolean.FALSE.equals(ReflectionTestUtils.getField(conversation, "compacting")));

        conversation.add(new UserMessage("第二问"));
        conversation.add(new AssistantMessage("第二答"));
        conversation.flush();

        verify(summarizer, after(QUIET_WINDOW_MS).times(1)).summarize(nullable(String.class), anyList());
    }

    @Test
    void conversationWithoutSummarizerNeverCompacts() {
        Conversation conversation = Conversation.of("device", 1, "session", "role", 1);

        for (int i = 0; i < 20; i++) {
            conversation.add(new UserMessage("问" + i));
            conversation.add(new AssistantMessage("答" + i));
        }
        conversation.flush();

        assertThat(conversation.rawMessages()).hasSize(40);
    }

    // 系统提示词由 messages() 每次现拼，不能被塞进历史
    @Test
    void systemMessageIsNotAddedToHistory() {
        Conversation conversation = conversation(4, 2);

        conversation.add(new SystemMessage("不该进历史"));

        assertThat(conversation.rawMessages()).isEmpty();
    }
}
