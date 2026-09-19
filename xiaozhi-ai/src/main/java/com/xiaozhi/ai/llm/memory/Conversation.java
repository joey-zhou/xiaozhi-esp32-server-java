package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.ai.llm.TokenEstimator;
import com.xiaozhi.common.AppVirtualThreads;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadFactory;

/**
 * Conversation 是一个 对应于 sys_message 表的，但高于 sys_message 的一个抽象实体。
 * deviceID, roleID, sessionID, 实质构成了一次Conversation的全局唯一ID。这个ID必须final 的。
 * 在关系型数据库里，可以将deviceID, roleID, sessionID 建一个组合索引，注意顺序sessionID放在最后。
 * 在图数据库里， conversation label的节点，连接 device节点、role节点。
 * deviceID与roleID本质上不是Conversation的真正属性，而是外键，代表连接的2个对象。
 * 只有sessionID是真正挂在Conversation的属性。
 * <p>
 * 对话记忆只有这一种：消息窗口加超限压缩。上下文 token 到预算或条数到上限时，后台把最近几条之外的完整对话组
 * 交给 {@link Summarizer} 合成摘要，成功后移出上下文。不注入 Summarizer 时是纯内存对话，不做压缩。
 * <p>
 * 上下文大小优先信模型回传的输入 token：不带工具调用的一轮，它就是上一次实际送出的全部内容。之后新增的消息用
 * {@link TokenEstimator} 累加；工具调用轮次 Spring AI 会把两次调用的用量加在一起，接近真实值的两倍，按估算值的
 * 倍数识别后忽略；拿不到用量的厂商全程估算，系统提示词与工具定义这些对话看不到的部分按固定余量计。
 * <p>
 * Conversation 不负责存储：历史与摘要由工厂加载后传入。
 */
@Slf4j
public class Conversation extends ConversationIdentifier {
    /** 角色系统提示词模板，占位符 $role_section$、$location_line$ */
    private static final PromptTemplate ROLE_SYSTEM_PROMPT_TEMPLATE = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
            .resource(new ClassPathResource("/prompts/role_system_prompt.md", Conversation.class))
            .build();

    /** 最后一条历史距今超过这个时长，视为上一段对话已经结束，建对话时先压缩一批 */
    private static final Duration STALE_HISTORY = Duration.ofHours(1);
    // 压缩连续失败的退避：每次失败后按 2^n 递增等待时间，避免每轮对话都重试打爆大模型
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(30);
    // 压缩持续失败时，未压缩消息允许堆积的硬上限（超过则丢弃最旧的一组，避免上下文无限膨胀）
    private static final int MAX_PENDING_MULTIPLIER = 4;
    // 模型回传的输入 token 超过估算值这么多倍，说明是工具调用轮次两次调用累加的结果，不能当上下文大小
    private static final double CUMULATIVE_USAGE_RATIO = 1.5;

    /** 压缩线程从模型流式回调里拉起，不能继承回调线程的上下文类加载器 */
    private static final ThreadFactory COMPACT_THREADS = AppVirtualThreads.factory("conversation-compact-");

    @Getter
    private final String roleDesc;
    @Getter
    private final Integer userId;
    private final String sessionId;
    private final Summarizer summarizer;
    private final int maxMessages;
    private final int tokenBudget;
    private final int promptOverhead;
    private final int keepMessages;

    // 以下状态都由 this 锁保护
    private final List<Message> messages = new ArrayList<>();
    private String summary;
    // 最近一次可信的模型输入 token，以及带回它的那条助手消息：从这条起（含）的消息都不在那次用量里，要另外估算
    private int baselineTokens;
    private Message baselineMessage;
    private boolean compacting;
    // 压缩进行中收到 flush，等这批结束后接着把剩下的全压掉
    private boolean flushPending;
    private int consecutiveFailures;
    private Instant nextRetryAt = Instant.EPOCH;
    // 会话已被删除：不再开始压缩
    private volatile boolean discarded;

    /**
     * 纯内存对话，不加载历史、不压缩。走 builder 而不是构造器链，省掉一长串占位的 null 与 0。
     *
     * @param ownerId   聊天参与者标识（设备场景: deviceId, Web 场景: web:userId）
     * @param roleId    角色ID
     * @param sessionId 会话ID
     * @param roleDesc  角色描述（静态，构造时确定）
     * @param userId    用户ID（消息持久化需要）
     */
    public static Conversation of(String ownerId, Integer roleId, String sessionId, String roleDesc, Integer userId) {
        return builder()
                .ownerId(ownerId)
                .roleId(roleId)
                .sessionId(sessionId)
                .roleDesc(roleDesc)
                .userId(userId)
                .build();
    }

    /**
     * @param history     已落库的历史，从上次摘要之后开始
     * @param summary     上次摘要，没有为 null
     * @param summarizer  超限时合成摘要，为 null 时不压缩
     * @param maxMessages    条数上限，拿不到用量也估不准时的兜底
     * @param tokenBudget    上下文 token 预算，到了就压缩
     * @param promptOverhead 全程估算时给系统提示词与工具定义预留的 token
     * @param keepMessages   压缩时保留最近的多少条不动
     */
    @Builder
    public Conversation(String ownerId, Integer roleId, String sessionId, String roleDesc, Integer userId,
                        List<Message> history, String summary, Summarizer summarizer,
                        int maxMessages, int tokenBudget, int promptOverhead, int keepMessages) {
        super(ownerId, roleId, sessionId);
        Assert.notNull(ownerId, "ownerId must not be null");
        Assert.notNull(roleId, "roleId must not be null");
        Assert.notNull(sessionId, "sessionId must not be null");
        if (summarizer != null) {
            Assert.state(maxMessages > 0, "maxMessages must be greater than 0");
            Assert.state(tokenBudget > 0, "tokenBudget must be greater than 0");
            Assert.state(promptOverhead >= 0, "promptOverhead must not be negative");
            Assert.state(keepMessages >= 0, "keepMessages must not be negative");
        }
        this.sessionId = sessionId;
        this.roleDesc = roleDesc;
        this.userId = userId;
        this.summarizer = summarizer;
        this.maxMessages = maxMessages;
        this.tokenBudget = tokenBudget;
        this.promptOverhead = promptOverhead;
        this.keepMessages = keepMessages;
        this.summary = summary;
        if (history != null && !history.isEmpty()) {
            restore(history);
        }
    }

    private void restore(List<Message> history) {
        int size;
        synchronized (this) {
            messages.addAll(history);
            dropLeadingOrphans();
            size = messages.size();
        }
        if (size >= 2 && Duration.between(MessageTimeMetadata.getTimeMillis(history.getLast()), Instant.now())
                .compareTo(STALE_HISTORY) >= 0) {
            log.info("{}的最后一条历史已超过{}小时，上一段对话已经结束，先全部压缩", getOwnerId(), STALE_HISTORY.toHours());
            compact(true, true);
        }
    }

    /**
     * 丢掉队首那段没有用户提问的消息。按条数取最后 N 条可能正好从工具链中间开始，
     * 带孤儿 ToolResponseMessage 的历史会被 provider 直接拒绝。
     */
    private void dropLeadingOrphans() {
        int orphans = MessageGroups.leadingOrphanSize(messages);
        if (orphans > 0) {
            log.info("{}加载的历史从对话组中间开始，丢弃开头{}条无主消息", getOwnerId(), orphans);
            messages.subList(0, orphans).clear();
        }
    }

    public String sessionId() {
        return sessionId;
    }

    /**
     * 角色系统提示词：角色设定、设备对话约束、位置。只放会话期内稳定的内容，System Prompt 会话内必须保持不变；
     * 逐条消息的元数据（时间戳、说话人、情绪）由 UserMessageAssembler 拼在每条 UserMessage 前缀里。
     */
    public SystemMessage roleSystemMessage(ConversationContext context) {
        String roleSection = StringUtils.hasText(roleDesc)
                ? "角色设定：" + System.lineSeparator() + roleDesc + System.lineSeparator()
                : "";
        String location = context != null ? context.location() : null;
        String locationLine = StringUtils.hasText(location)
                ? System.lineSeparator() + "当前位置：" + location + "。用户如果说自己现在在别的地方，以用户说的为准。"
                : "";
        String text = ROLE_SYSTEM_PROMPT_TEMPLATE.render(Map.of(
                "role_section", roleSection,
                "location_line", locationLine));
        return new SystemMessage(text.strip());
    }

    /**
     * 送给 LLM 的消息列表：角色系统提示词、摘要、历史。
     * <p>
     * 对每条消息走一次 {@link UserMessageAssembler#assemble(Message)}：
     * UserMessage 按其 metadata 装配带前缀的副本送给 LLM，非 UserMessage 原样透传。
     * in-memory 的消息始终是"裸文本 + 结构化 metadata"。
     */
    public synchronized List<Message> messages(ConversationContext context) {
        List<Message> result = new ArrayList<>();
        result.add(roleSystemMessage(context));
        if (StringUtils.hasText(summary)) {
            // 多条SystemMessage在主流模型（OpenAI、Qwen、DeepSeek）中均已验证可用
            result.add(new SystemMessage("下面是你与用户最近聊天内容的摘要：\n" + summary));
        }
        result.addAll(messages);
        return result.stream().map(UserMessageAssembler::assemble).toList();
    }

    public synchronized List<Message> messages() {
        return messages(ConversationContext.EMPTY);
    }

    /**
     * 原始消息快照（不含系统提示词与摘要，文本保持"裸文本"，metadata 未拼前缀）。
     * 返回副本，调用方遍历期间本会话仍可继续追加消息。
     */
    public synchronized List<Message> rawMessages() {
        return List.copyOf(messages);
    }

    /**
     * 清空内存里的消息列表，已落库的历史不受影响。
     */
    public synchronized void clear() {
        messages.clear();
    }

    /**
     * 当前上下文的 token 数。有可信用量时以它为基准，加上之后新增消息的估算；
     * 没有（厂商不回传、基准消息已被压缩）时全量估算摘要与消息，再加上系统部分的固定余量。
     */
    public synchronized int contextTokens() {
        int start = baselineMessage == null ? -1 : indexOfIdentity(baselineMessage);
        if (start < 0) {
            return promptOverhead + TokenEstimator.estimate(summary) + TokenEstimator.estimate(messages);
        }
        return baselineTokens + TokenEstimator.estimate(messages.subList(start, messages.size()));
    }

    /**
     * 添加消息。助手消息元数据里挂着本轮用量时更新上下文基准，并判断是否需要压缩。
     */
    public void add(Message message) {
        synchronized (this) {
            if (message instanceof AssistantMessage
                    && message.getMetadata().get(ChatMemory.USAGE_KEY) instanceof Usage usage
                    && usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                adoptUsage(message, usage.getPromptTokens());
            }
            if (message instanceof UserMessage || message instanceof AssistantMessage
                    || message instanceof ToolResponseMessage) {
                messages.add(message);
            }
        }
        // 只在添加 AssistantMessage 时判断，一轮收尾时才凑得出完整的对话组
        if (message instanceof AssistantMessage) {
            compact(false, false);
        }
    }

    /**
     * 本轮用量能否作为基准，要在助手消息入列前判断：用量对应的是这条回复之前送出的内容。
     * 已有基准时，用量超过估算值 {@link #CUMULATIVE_USAGE_RATIO} 倍说明本轮带了工具调用，两次调用的用量被累加，忽略；
     * 还没有基准时无从比较，先收下，下一轮不带工具的用量会把它纠正回来。
     */
    private void adoptUsage(Message reply, int promptTokens) {
        boolean anchored = baselineMessage != null && indexOfIdentity(baselineMessage) >= 0;
        int expected = contextTokens();
        if (anchored && promptTokens > expected * CUMULATIVE_USAGE_RATIO) {
            log.debug("{}本轮输入 token {} 远超估算的 {}，按工具调用轮次的累计用量忽略", getOwnerId(), promptTokens, expected);
            return;
        }
        baselineTokens = promptTokens;
        baselineMessage = reply;
    }

    private int indexOfIdentity(Message target) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将工具调用链（模型的 tool_call 请求 + 工具执行结果）作为原子操作添加到消息列表
     */
    public synchronized void addToolCallChain(AssistantMessage toolCallMsg, ToolResponseMessage toolResponse) {
        messages.add(toolCallMsg);
        messages.add(toolResponse);
    }

    /**
     * 用截断后的消息替换原消息（按对象身份定位），原消息不在列表里则不做任何事
     */
    public synchronized void replace(Message original, Message replacement) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) == original) {
                messages.set(i, replacement);
                if (baselineMessage == original) {
                    baselineMessage = replacement;
                }
                return;
            }
        }
    }

    /**
     * 把消息插到 anchor 所在轮次的末尾（下一条 UserMessage 之前）；anchor 不在列表里则追加到末尾。
     * 用于迟到的打断收尾：该轮的用户消息之后可能已经有了新一轮消息。
     */
    public synchronized void insertAfterTurn(Message anchor, List<Message> toInsert) {
        int index = messages.size();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == anchor) {
                index = i + 1;
                while (index < messages.size() && !(messages.get(index) instanceof UserMessage)) {
                    index++;
                }
                break;
            }
        }
        messages.addAll(index, toInsert);
    }

    /**
     * 按对象身份移除消息
     */
    public synchronized void remove(Message message) {
        messages.removeIf(m -> m == message);
    }

    /**
     * 会话结束时把剩下的完整对话组全部压缩：摘要落库，下次续接只加载摘要之后的消息。
     * 正在压缩时记下来，等那批结束后接着压。
     */
    public void flush() {
        compact(true, true);
    }

    /**
     * 会话已被用户删除：不再开始新的压缩，免得把刚删掉的内容写进摘要。
     */
    public void discard() {
        discarded = true;
    }

    /**
     * @param force 不看上限直接压缩
     * @param all   压缩全部完整对话组，否则保留最近 keepMessages 条
     */
    private void compact(boolean force, boolean all) {
        if (summarizer == null || discarded) {
            return;
        }
        List<Message> batch;
        int size;
        int tokens;
        synchronized (this) {
            if (compacting) {
                if (all) {
                    flushPending = true;
                }
                return;
            }
            size = messages.size();
            tokens = contextTokens();
            if (size == 0 || (!force && size < maxMessages && tokens < tokenBudget)) {
                return;
            }
            // 退避期间也要裁到硬上限，否则退避的半小时里上下文只增不减，直到撞上模型的上下文长度
            int hardCap = maxMessages * MAX_PENDING_MULTIPLIER;
            if (consecutiveFailures > 0 && size > hardCap) {
                int dropSize = MessageGroups.alignedPrefixSize(messages, size - hardCap);
                if (dropSize > 0) {
                    log.error("{}摘要连续失败{}次，未压缩消息堆积到{}条已超过硬上限{}，丢弃最旧{}条防止上下文无限膨胀",
                            getOwnerId(), consecutiveFailures, size, hardCap, dropSize);
                    messages.subList(0, dropSize).clear();
                    size = messages.size();
                }
            }
            // 允许失败后立即重试一次（单次抖动很常见），连续失败到第二次才开始退避
            if (!force && consecutiveFailures > 1 && Instant.now().isBefore(nextRetryAt)) {
                return;
            }
            // 最近几条留在上下文里，其余整组压掉；token 超预算时哪怕只剩最近几条也至少压一组。
            // 批次补齐到对话组边界，工具链整组压缩，不能留下孤儿 tool 消息；当前这轮还没收尾时凑不出完整的一组
            int desired = all ? size : Math.max(size - keepMessages, tokens >= tokenBudget ? 1 : 0);
            int batchLength = desired <= 0 ? 0 : MessageGroups.alignedPrefixSize(messages, desired);
            if (batchLength <= 0) {
                return;
            }
            batch = new ArrayList<>(messages.subList(0, batchLength));
            compacting = true;
        }
        log.info("{}的对话累计{}条、约{}个输入 token，压缩最早的{}条", getOwnerId(), size, tokens, batch.size());
        COMPACT_THREADS.newThread(() -> compact(batch)).start();
    }

    /**
     * compacting 无论怎么退出都要复位，否则这段对话此后永不压缩。
     */
    private void compact(List<Message> batch) {
        int removed;
        boolean flushAll;
        try {
            removed = summarize(batch);
        } finally {
            synchronized (this) {
                compacting = false;
                flushAll = flushPending;
                flushPending = false;
            }
        }
        if (flushAll) {
            compact(true, true);
        } else if (removed > 0) {
            // 一条都没移除时不再递归，避免同一批次反复压缩
            compact(false, false);
        }
    }

    /**
     * 把这批消息合进摘要；摘要成功后这批消息移出上下文。
     *
     * @return 本批实际移出上下文的消息数，摘要失败为 0
     */
    private int summarize(List<Message> batch) {
        if (discarded) {
            return 0;
        }
        String previous;
        synchronized (this) {
            previous = summary;
        }
        String newSummary;
        try {
            newSummary = summarizer.summarize(previous, batch);
        } catch (Exception | LinkageError e) {
            log.error("{}对话摘要失败，本批消息保留在上下文里", getOwnerId(), e);
            synchronized (this) {
                consecutiveFailures++;
                long delaySeconds = Math.min(
                        RETRY_BASE_DELAY.getSeconds() << Math.min(consecutiveFailures - 1, 10),
                        RETRY_MAX_DELAY.getSeconds());
                nextRetryAt = Instant.now().plusSeconds(delaySeconds);
                log.warn("{}对话摘要连续失败{}次，{}秒后才允许下一次重试", getOwnerId(), consecutiveFailures, delaySeconds);
            }
            return 0;
        }
        int removed;
        synchronized (this) {
            // 按引用移除本批消息，内容相同的其它消息不受影响
            removed = removeByIdentity(batch);
            summary = newSummary;
            consecutiveFailures = 0;
            nextRetryAt = Instant.EPOCH;
            // 基准还在上下文里时把压掉的部分从基准里扣掉；基准消息本身被压掉则回到全量估算
            if (baselineMessage != null && indexOfIdentity(baselineMessage) >= 0) {
                baselineTokens = Math.max(0, baselineTokens - TokenEstimator.estimate(batch));
            } else {
                baselineMessage = null;
            }
        }
        return removed;
    }

    private int removeByIdentity(List<Message> targets) {
        int before = messages.size();
        for (Message target : targets) {
            messages.removeIf(message -> message == target);
        }
        return before - messages.size();
    }
}
