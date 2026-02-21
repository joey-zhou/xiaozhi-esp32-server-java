package com.xiaozhi.dialogue.llm.tool.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.stereotype.Component;

/**
 * 在这里进行AssistantMessage消息的处理。
 * 这里所获得的ChatResponse对象实例实际是来自于 MessageAggregator 所聚合的，即使在stream调用里也是生效的。
 * @see new MessageAggregator().aggregate(chatResponseFlux, observationContext::setResponse);
 *
 * 在 观察者 onStop时，可从Context里获得Request与Response。当前只是在这里保存Assistant消息,具体的细节逻辑，由Conversation处理。
 * TODO 将入库持久化的职能移至此处，可统一在此class保存用户输入与Ai输出。避免逻辑过于分散。
 * 注意：Message在没有持久化前，是不会有messageId的。需要依靠createTime标识消息唯一性。
 * 问：是否需要把content为空和角色为tool的入库?
 * 答：目前不入库（这类主要是function_call的二次调用llm进行总结时的过程消息）。TODO 未来可考虑将工具结果也一起入库。
 *
 * TODO 考虑将 时间戳 移至此处，避免散落在其它地方。
 * 需要时间戳的场景：
 * 1. 数据库表的createTime字段。
 * 2. 音频文件存储。（当前是在Player保存为本地文件）如何确保Player能拿到 时间戳？ 办法一：setter 至Synthesizer and Player。
 *
 * 此类仅记录相关信息，但不能反向影响业务逻辑。也就是必须假设拆除此class，不影响对话。
 * 这个假设不成立，因为AssistantMessage 将会由此Handler 添加回到 Conversation 进而影响下一轮对话
 * 参考PromptChatMemoryAdvisor，它在LLM流式输出时是如何添加消息至 Conversation的？
 *
 */
@Deprecated
@Component
public class ChatModelObservationHandler  implements ObservationHandler<ChatModelObservationContext> {
    private static final Logger logger = LoggerFactory.getLogger(ChatModelObservationHandler.class);

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

}
