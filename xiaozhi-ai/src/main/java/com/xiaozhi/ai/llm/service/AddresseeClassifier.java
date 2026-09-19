package com.xiaozhi.ai.llm.service;

import com.xiaozhi.ai.llm.JsonMode;
import com.xiaozhi.ai.llm.memory.MessageHistoryFormatter;
import com.xiaozhi.ai.utils.LenientJson;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
/**
 * 设备正在说话时用户插进来一句，判定这句是不是在对设备说：不是则续播，是则确认打断。
 * <p>
 * 用角色当前配置的模型单独请求一次，不挂工具，只回一个布尔。判不出来一律按打断处理——
 * 漏掉真指令要用户重说一遍，比多接一句话难受得多。
 */
@Slf4j
@Service
public class AddresseeClassifier {

    /** 判定耗时上限，必须远小于播放暂停的保护上限，超时按打断处理 */
    private static final long VERDICT_TIMEOUT_MS = 1500;

    /** 带给模型的历史消息条数，取会话尾部的用户与助手消息 */
    static final int HISTORY_SIZE = 5;

    private static final Set<MessageType> DIALOGUE_TYPES = Set.of(MessageType.USER, MessageType.ASSISTANT);

    /** 转换器构造时要生成 JSON Schema，每次插话建一份太贵；解析前先经 LenientJson 补齐结构，再用放宽的解析器读 */
    private static final BeanOutputConverter<Verdict> VERDICT_CONVERTER =
            new BeanOutputConverter<>(Verdict.class, LenientJson.mapper(), LenientJson::object);

    /** 模型调用是阻塞的，不能占用 ForkJoinPool.commonPool */
    private static final Executor CLASSIFY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** 模型漏字段时 directed 为 null，不能用基本类型接 */
    record Verdict(Boolean directed) {
    }

    private final PromptTemplate template = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
            .resource(new ClassPathResource("/prompts/addressee_prompt.md", getClass()))
            .build();

    /**
     * 这句插话是不是在对设备说。
     *
     * @param chatModel       角色当前配置的模型，为 null 时不判定
     * @param history         会话消息，内部只取尾部若干条用户与助手消息
     * @param spokenSentences 设备本轮已经说出口的句子
     * @param utterance       插进来的整句
     * @return true 表示确认打断，超时、出错、解析不出结果一律返回 true
     */
    public boolean directedAtDevice(ChatModel chatModel, List<Message> history,
                                    List<String> spokenSentences, String utterance) {
        if (chatModel == null || !StringUtils.hasText(utterance)) {
            return true;
        }
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(
                () -> classify(chatModel, history, spokenSentences, utterance), CLASSIFY_EXECUTOR);
        try {
            return future.get(VERDICT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            log.warn("插话判定超过 {}ms 未返回，按打断处理 - text: {}", VERDICT_TIMEOUT_MS, utterance);
        } catch (ExecutionException e) {
            log.warn("插话判定失败，按打断处理 - text: {}", utterance, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待插话判定被中断，按打断处理 - text: {}", utterance);
        }
        return true;
    }

    private boolean classify(ChatModel chatModel, List<Message> history,
                             List<String> spokenSentences, String utterance) {
        String prompt = template.render(Map.of(
                "history", MessageHistoryFormatter.format(recentDialogue(history)),
                "spoken", spokenSentences == null ? "" : String.join("", spokenSentences),
                "utterance", utterance));
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        String response = JsonMode.call(chatModel, options -> {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(prompt);
            if (options != null) {
                request = request.options(options);
            }
            return request.call().content();
        });
        Verdict verdict = VERDICT_CONVERTER.convert(response);
        if (verdict == null || verdict.directed() == null) {
            log.warn("插话判定结果读不出 directed，按打断处理 - response: {}", response);
            return true;
        }
        log.info("插话判定: directed={} - text: {}", verdict.directed(), utterance);
        return verdict.directed();
    }

    /**
     * 只取尾部的用户与助手消息，工具消息不进判定上下文
     */
    private static List<Message> recentDialogue(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Message> dialogue = history.stream()
                .filter(m -> DIALOGUE_TYPES.contains(m.getMessageType()))
                .toList();
        return dialogue.size() <= HISTORY_SIZE
                ? dialogue
                : dialogue.subList(dialogue.size() - HISTORY_SIZE, dialogue.size());
    }
}
