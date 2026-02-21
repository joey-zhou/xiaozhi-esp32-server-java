package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;

/**
 * 当前的10秒断链只是为了省电，而一般的我们日常自然人之间的对话Conversation 不会是以10秒那么短的时间间隔作为感知的一段对话。
 * SessionID是一个技术名词，不是一个用户关心的概念，即使开机与关机与不是用户关心的。
 * 既然用户不是真的关心session id , 那就可以batch为单位给到用户家长去看。
 * 考虑到云端API越来越多的支持Cache，这个Batch做得更大些才有更好的收益。
 *
 * - 可能发生情形
 *
 * 1. Conversation对话轮数太长了，需要切成几个Batch进行Summarize.
 * 2. Conversation对话轮数太短了，都不值得进行一次Summarize。
 * 3. 在进行了一轮summarize后没有太长时间对话轮数还比较短，就结束了 Conversation 了。
 * 4. 在结束了Conversation后，没有多长时间又登录进来继续聊天了。
 *
 * 引入 CONVERSATION_INTERVAL_HOURS 是为了支持业务感知上的一段对话的概念（脱离技术意义上的关于连接会话session_id）。
 */
@Service
public class SummaryConversationFactory implements ConversationFactory{
    private static final Logger logger = LoggerFactory.getLogger(SummaryConversationFactory.class);
    private final ChatMemory chatMemory;
    private final SystemPromptTemplate SUMMARIZER_SYSTEM_PROMPT_TEMPLATE;
    private final PromptTemplate initSummarizerPromptTemplate ;
    private final PromptTemplate againSummarizerPromptTemplate ;


    // TODO 涉及循环依赖，暂时用Lazy注解解决。后续再重新从设计层面考虑更优的解决方法。
    // TODO 用于Summary的ChatModel不需要工具调用、MCP、ToolCallingManager。

    @Autowired
    @Lazy
    private  ChatModelFactory chatModelFactory;

    @Value("${conversation.max-messages:8}")
    private int maxMessages;
    @Value("${conversation.batch-size:4}")
    private int batchSize;


    @Autowired
    public SummaryConversationFactory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        SUMMARIZER_SYSTEM_PROMPT_TEMPLATE = new SystemPromptTemplate(new ClassPathResource("/prompts/system_prompt_with_summary.md", getClass()));
        this.initSummarizerPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
                .resource(new ClassPathResource("/prompts/init_summarizer.md", getClass()))
                .build();
        this.againSummarizerPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
                .resource(new ClassPathResource("/prompts/again_summarizer.md", getClass()))
                .build();
    }

    @Override
    public Conversation initConversation(SysDevice device, SysRole role, String sessionId) {
        logger.debug("初始化SummaryConversation基础配置参数，maxMessages：{}，batchSize：{}",maxMessages,batchSize);


        ChatModel chatModel = chatModelFactory.takeChatModel(role);
        // 测试时，可将batchSeconds调小。实际生产的默认值可以先考虑：20,16,60。
        return SummaryConversation.builder()
                .device(device)
                .role(role)
                .sessionId(sessionId)
                .maxMessages(maxMessages)
                .batchSize(batchSize)
                .chatMemory(chatMemory)
                .chatModel(chatModel)
                .initSummarizerPromptTemplate(initSummarizerPromptTemplate)
                .againSummarizerPromptTemplate(againSummarizerPromptTemplate)
                .build();
    }
}
