package com.xiaozhi.dialogue.llm.memory;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CustomMessageChatMemoryAdvisor implements BaseChatMemoryAdvisor {

    private final ChatMemory chatMemory;

    private final String defaultConversationId;

    private String deviceId;

    private Integer roleId;

    private final int order;

    private final Scheduler scheduler;

    private CustomMessageChatMemoryAdvisor(ChatMemory chatMemory, String defaultConversationId,String deviceId,Integer roleId, int order,
                                     Scheduler scheduler) {
        Assert.notNull(chatMemory, "chatMemory cannot be null");
        Assert.hasText(defaultConversationId, "defaultConversationId cannot be null or empty");
        Assert.notNull(scheduler, "scheduler cannot be null");
        this.chatMemory = chatMemory;
        this.defaultConversationId = defaultConversationId;
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.order = order;
        this.scheduler = scheduler;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public Scheduler getScheduler() {
        return this.scheduler;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String conversationId = getConversationId(chatClientRequest.context(), this.defaultConversationId);

        // 1. Retrieve the chat memory for the current conversation.
        List<Message> memoryMessages = this.chatMemory.get(conversationId);

        // 2. Advise the request messages list.
        List<Message> processedMessages = new ArrayList<>(memoryMessages);
        processedMessages.addAll(chatClientRequest.prompt().getInstructions());

        // 3. Create a new request with the advised messages.
        ChatClientRequest processedChatClientRequest = chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().mutate().messages(processedMessages).build())
                .build();

        // 4. Add the new user message to the conversation memory.
        UserMessage userMessage = processedChatClientRequest.prompt().getUserMessage();
        userMessage.getMetadata().put("deviceId", deviceId);
        userMessage.getMetadata().put("roleId",roleId);
        userMessage.getMetadata().put("messageType","NORMAL");
        this.chatMemory.add(conversationId, userMessage);

        return processedChatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        List<Message> assistantMessages = new ArrayList<>();
        if (chatClientResponse.chatResponse() != null) {
            List<Generation> list = chatClientResponse.chatResponse().getResults();
            for (Generation generation : list) {
                Message message = generation.getOutput();
                message.getMetadata().put("deviceId", deviceId);
                message.getMetadata().put("roleId",roleId);
                message.getMetadata().put("messageType","NORMAL");
                if(!generation.getMetadata().isEmpty()){
                    if(generation.getMetadata().keySet().contains("toolName")){
                        message.getMetadata().put("messageType","FUNCTION_CALL");
                    }
                }
                log.info("after message = {}", JSON.toJSONString(message));
                assistantMessages.add(message);
            }
        }
        this.chatMemory.add(this.getConversationId(chatClientResponse.context(), this.defaultConversationId),
                assistantMessages);
        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        // Get the scheduler from BaseAdvisor
        Scheduler scheduler = this.getScheduler();

        // Process the request with the before method
        return Mono.just(chatClientRequest)
                .publishOn(scheduler)
                .map(request -> this.before(request, streamAdvisorChain))
                .flatMapMany(streamAdvisorChain::nextStream)
                .transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
                        response -> this.after(response, streamAdvisorChain)));
    }

    public static CustomMessageChatMemoryAdvisor.Builder builder(ChatMemory chatMemory) {
        return new CustomMessageChatMemoryAdvisor.Builder(chatMemory);
    }

    public static final class Builder {

        private String conversationId = ChatMemory.DEFAULT_CONVERSATION_ID;

        private String deviceId;

        private Integer roleId;

        private int order = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

        private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

        private ChatMemory chatMemory;

        private Builder(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
        }

        /**
         * Set the conversationId id.
         * @param conversationId the conversationId id
         * @return the builder
         */
        public CustomMessageChatMemoryAdvisor.Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public CustomMessageChatMemoryAdvisor.Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public CustomMessageChatMemoryAdvisor.Builder roleId(Integer roleId) {
            this.roleId = roleId;
            return this;
        }
        /**
         * Set the order.
         * @param order the order
         * @return the builder
         */
        public CustomMessageChatMemoryAdvisor.Builder order(int order) {
            this.order = order;
            return this;
        }

        public CustomMessageChatMemoryAdvisor.Builder scheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Build the advisor.
         * @return the advisor
         */
        public CustomMessageChatMemoryAdvisor build() {
            return new CustomMessageChatMemoryAdvisor(this.chatMemory, this.conversationId, deviceId,roleId,this.order, this.scheduler);
        }

    }

}
