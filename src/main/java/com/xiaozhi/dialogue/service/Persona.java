package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysMessageService;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


/**
 * 人物角色、虚拟形象，描述角色的属性和行为。Domain Entity: CharacterRole(聊天角色,Persona)，管理对话历史记录，管理对话工具调用等。
 * 聚合着ChatModel、TTS(Synthersizer)、Player
 *
 * Persona 与ChatSession主要是两个关联：
 * 一是收到消息时，需要从ChatSession 传导给到Persona，然后Persona将消息传递给ChatModel。
 * 二是发送消息时，需要从Persona 将消息传递给ChatSession。
 *
 *  * 保存用户音频数据
 *  * TODO 思考：将用户音频保存为文件，其Path如何关联到UserMessage，此时可能还没有保存到数据库。
 *
 * 生命周期不同时间节点的几个事件：
 * 1. 接收到 UserSpeech, 获得完整语音时;
 * 2. ASR识别出 UserText,已进行STT语音识别后，获得了文本时。
 * 3. LLM响应AssistantText ,已进行LLM生成消息后，获得了PromptTokens时。延迟到这个时间点触发持久化可能是好的选择。
 * 4. TTS合成AssistantSpeech,
 * 5. Player播放完语音。
 *
 * ID作为实体对象的唯一标识，是数据库里的重要表达，但对于应用来说不必要。
 * 如果Persona不关心持久化存储，也就不必关心消息ID，但需要关心事件发生的时间点及相互关联。
 * Persona and Conversation 都是属于Domain ，不属于Infrastructure，所以都可以不考虑持久化存储。
 * 消息时间戳这些都属于 Observation信息。（可以参考 ChatModel 将持久化的职能以ObservationHandler的形式实现）
 * SysMessage 也可以属于Domain Entity，但具体如何持久化存储是应当由外部具体实现类实现。
 * SysMessage = AbstractMessage + ConversationIdentifier + Usage + AudioPath + Duration + createdAt
 * TODO 这个公式，实际可以将UserMessage+ AssistantMessage 组合为 Dialogue(甚至可以继承于 ObservationContext)
 *  Dialogue对象应该作为方法内局部变量，而非 Persona属性。
 * ConversationIdentifier = deviceId + sessionId + roleId
 * MessageIdentifier = ConversationIdentifier + time-stamp
 */
@Builder(toBuilder = true)
@Getter
public class Persona {

    private static final Logger logger = LoggerFactory.getLogger(Persona.class);
    private static final String TOOL_CONTEXT_SESSION_KEY = "session";

    private ChatSession session;

    private SttService sttService;
    //private SysConfig ttsConfig;
    //private SysConfig llmConfig;

    /**
     * 与LLM Provider通信的具体实现类
     */
    private ChatModel chatModel;
    private GoodbyeMessageSupplier goodbyeMessages;

    /**
     * 语音合成器
     */
    private Synthesizer synthesizer;
    /**
     * 语音播放器
     */
    private Player player;

    /**
     * 一个Session在某个时刻，只有一个活跃的Conversation。
     * 当切换角色时，Conversation应该释放新建。切换角色一般是不频繁的。
     */
    private Conversation conversation;

    /**
     * 可以考虑定义成一个Listener，用于监听在STT/LLM/TTS/Player等不同动作节点进要执行的回调，这样可以将如何保存消息等由外部注入，实现核心与辅助的分离。
     */
    private SysMessageService messageService;

    public Path chat(Flux<byte[]> audioSink){
        // 这个streamRecognition 是阻塞式的，不是异步的。
        final String userText = sttService.streamRecognition(audioSink);
        if (!StringUtils.hasText(userText)) {
            return null;
        }

        player.sendStt(userText);
        var userInstant = Instant.now();
        Path path = session.getAudioPath(MessageType.USER.getValue(), userInstant);
        var chatResponseFlux = chatStream(path,userInstant,new UserMessage(userText),true);
        synthesizer.synthesize(convert(chatResponseFlux));
        return path;

    }


    public Flux<ChatResponse> chatStream( UserMessage userMessage, boolean useFunctionCall) {

        // 获取对话时间戳
        Instant now = Instant.now();
        return chatStream(null,now, userMessage, useFunctionCall);
    }

    /**
     * 处理用户查询（流式方式）
     * TODO Path userSpeechPath 这个参数不合理，从一开始传递直到最后才使用。
     * @param userMessage         用户消息
     * @param useFunctionCall 是否使用函数调用
     */
    private Flux<ChatResponse> chatStream(Path userSpeechPath, Instant now, UserMessage userMessage, boolean useFunctionCall) {

        AtomicReference<Instant> ttft = new AtomicReference<>(null);

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(useFunctionCall ? session.getToolCallbacks() : new ArrayList<>())
                .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                .toolContext("conversationTimestamp", now.toEpochMilli())
                .build();

        conversation.add(userMessage);
        List<Message> messages = conversation.messages();
        Prompt prompt = new Prompt(messages, chatOptions);
        Flux<ChatResponse> chatFlux = chatModel.stream(prompt);
        chatFlux = chatFlux.doOnNext(chatResponse -> {
            Instant assistantMessageCreatedAt = Instant.now();
            boolean isFirst = ttft.compareAndSet(null,assistantMessageCreatedAt);
            if(isFirst && player instanceof PlayerWithOpusFile playerWithOpusFile){
                playerWithOpusFile.setAssistantMessageCreatedAt(assistantMessageCreatedAt);
            }
        });
        return new MessageAggregator().aggregate(chatFlux, chatResponse -> {
            boolean disturbed = isDisturbed(chatResponse);
            var toolCallDetails = session.drainToolCallDetails();
            Dialogue dialogue = Dialogue.builder()
                    .userMessage(userMessage)
                    .chatResponse(chatResponse)
                    .conversationId(conversation)
                    .userMessageCreatedAt(now)
                    .userSpeechPath(userSpeechPath)
                    .assistantMessageCreatedAt(ttft.get())
                    .disturbed(disturbed)
                    .toolCallDetails(toolCallDetails)
                    .build();
            // UserMessage 的时间戳应该在 Dialogue 中注入,与Conversation持有的是同一个UserMessage。
            dialogue.injectInstants();
            messageService.saveAll(dialogue.convert());
            if(disturbed){
                conversation.add(Conversation.ROLLBACK_MESSAGE);
            }else{
                // 不能再从ChatResponse里取AssistantMessage,因为已注入时间戳
                conversation.add(dialogue.getAssistantMessage());
            }

        });
    }

    /**
     * 默认情况下，启用工具调用
     * @param userMessage
     */
    public void chat(String userMessage){
        chat(userMessage,true);
    }

    /**
     * @param userMessage
     * @param useFunctionCall
     */
    public void chat(String userMessage, boolean useFunctionCall){
        Flux<ChatResponse> chatResponseFlux = chatStream(new UserMessage(userMessage), useFunctionCall);
        // 初始化对话状态
        synthesizer.synthesize(convert(chatResponseFlux));
    }

    /**
     * 检查当前Persona是否处于活跃状态（LLM生成中、TTS合成中、音频播放中等）。
     * 用于打断判断：只要管道中任何一层仍在工作，就应该被打断。
     */
    public boolean isActive() {
        if (synthesizer != null && synthesizer.isActive()) {
            return true;
        }
        return player != null && player.hasContent();
    }

    public void wakeUp(String text) {

        Assert.notNull(conversation, "conversation cannot be null");

        SysRole role = conversation.getRole();
        Assert.notNull(role, "role cannot be null");

        chat(text,false);
    }

    private boolean isDisturbed(ChatResponse chatResponse) {
        Generation generation = chatResponse.getResult();
        Assert.notNull(generation, "Generation is null from ChatResponse");

        // 判断用户消息是否属于可能影响后续对话效果的指令
        ChatGenerationMetadata generationMetadata = generation.getMetadata();
        // TODO 不再应该从session中获取工具列表，应该从McpServer获取。
        List<ToolCallback> toolCallbacks = session.getToolCallbacks();
        return isFuncitonCalling(generationMetadata, toolCallbacks);

    }

    /**
     * 检查元数据中是否包含工具调用标识。这里的“工具调用”指的是那些会影响对话效果的工具消息，例如“退出”、“切换角色”。
     * 这些的特殊用户指令会污染后续对话效果。
     * 有些工具调用的结果直接作为AssistantMessage加入对话历史并不会影响对话效果。它的AssistantMessage为正常消息。
     * @param generationMetadata
     * @param toolCallbacks
     * @return
     */

    private boolean isFuncitonCalling(ChatGenerationMetadata generationMetadata, List<ToolCallback> toolCallbacks){
        if(ToolExecutionResult.FINISH_REASON.equals(generationMetadata.getFinishReason())){
            String toolId = generationMetadata.get(ToolExecutionResult.METADATA_TOOL_ID);
            String toolName = generationMetadata.get(ToolExecutionResult.METADATA_TOOL_NAME);
            logger.info("工具调用id: {} , name: {}", toolId,toolName);

            if (StringUtils.hasText(toolName) &&
                    toolCallbacks.stream()
                            .filter(toolCallback -> toolCallback.getToolDefinition().name().equals(toolName))
                            .map(toolCallback -> toolCallback.getToolMetadata())
                            .filter(toolMetadata -> toolMetadata instanceof XiaozhiToolMetadata)
                            .map(toolMetadata -> (XiaozhiToolMetadata) toolMetadata)
                            .anyMatch(xiaozhiToolMetadata -> xiaozhiToolMetadata.rollback())) {
                logger.info("当前用户消息属于可能影响后续对话效果的指令`{}`，准备执行回滚。", toolName);
                return true;
            }
        }
        return false;
    }

    /**
     * 发送告别语并在播放完成后关闭会话
     *
     * @return 是否成功发送告别语
     */
    public void sendGoodbyeMessage() {
        if (session == null){
            return ;
        }
        // 告别语不需要保存opus音频文件，重置时间戳防止复用上一轮对话的值
        if (player instanceof PlayerWithOpusFile playerWithOpusFile) {
            playerWithOpusFile.setAssistantMessageCreatedAt(null);
        }
        player.setFunctionAfterChat(() -> {
            session.setPersona(null);
            session.setPlayer(null);
            conversation.clear();
            session.close();
        });
        if(goodbyeMessages!=null){
            // 随机选择一条告别语
            String goodbyeMessage = goodbyeMessages.get();

            // 直接处理告别语，不通过LLM
            Optional.ofNullable(session.getPersona())
                    .map(Persona::getSynthesizer)
                    .ifPresent(synthesizer -> {
                        synthesizer.synthesize(goodbyeMessage);
                    });
        }else{
            chat("我有事先忙了，再见！",false);
        }

    }

    private Flux<String> convert(Flux<ChatResponse> chatResponseFlux) {
        return chatResponseFlux.mapNotNull(ChatResponse::getResult)
                .mapNotNull(Generation::getOutput)
                .mapNotNull(AssistantMessage::getText);

    }

    // Getters
    public ChatSession getSession() { return session; }
    public SttService getSttService() { return sttService; }
    public ChatModel getChatModel() { return chatModel; }
    public GoodbyeMessageSupplier getGoodbyeMessages() { return goodbyeMessages; }
    public Synthesizer getSynthesizer() { return synthesizer; }
    public Player getPlayer() { return player; }
    public Conversation getConversation() { return conversation; }
    public SysMessageService getMessageService() { return messageService; }

    // Setters
    public void setSession(ChatSession session) { this.session = session; }
    public void setPlayer(Player player) { this.player = player; }
    public void setSynthesizer(Synthesizer synthesizer) { this.synthesizer = synthesizer; }
    public java.nio.file.Path getAudioFilePath() { return player != null ? player.getAudioFilePath() : null; }
}
