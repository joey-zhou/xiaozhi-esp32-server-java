package com.xiaozhi.ai.llm.memory;

import lombok.Builder;
import org.springframework.ai.chat.messages.*;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
/**
 * 限定消息条数（消息窗口）的Conversation实现。根据不同的策略，可实现聊天会话的持久化、加载、清除等功能。
 * 短期记忆，只能记住当前对话有限的消息条数（多轮）。
 */
@Slf4j
public class MessageWindowConversation extends Conversation {
    private final int maxMessages;
    /**
     * 可切换加载维度的构造器。由 Lombok {@link Builder} 生成静态工厂 {@code builder()} 与链式 setter。
     * <ul>
     *   <li>{@code sessionScoped=false}（默认）：按 ownerId + roleId 查 {@link ChatMemory#find(String, int, int)}，设备场景跨 session 聚合</li>
     *   <li>{@code sessionScoped=true}：按 sessionId 查 {@link ChatMemory#findBySession(String, int)}，Web 场景按会话隔离</li>
     * </ul>
     */
    @Builder
    public MessageWindowConversation(String ownerId, Integer roleId, String sessionId, String roleDesc, Integer userId,
                                      int maxMessages, ChatMemory chatMemory, boolean sessionScoped){
        super(ownerId, roleId, sessionId, roleDesc, userId);
        this.maxMessages = maxMessages;

        List<Message> history = sessionScoped
                ? chatMemory.findBySession(sessionId, maxMessages)
                : chatMemory.find(ownerId, roleId, maxMessages);
        log.info("加载对话历史: sessionScoped={}, ownerId={}, sessionId={}, size={}",
                sessionScoped, ownerId, sessionId, history.size());
        super.messages.addAll(history);
        dropLeadingOrphans();
    }

    /**
     * 丢掉队首那段没有用户提问的消息。按条数取最后 N 条可能正好从工具链中间开始，
     * 此时历史长度未超窗口、裁剪循环不会执行，请求就会以孤儿 ToolResponseMessage 开头被 provider 拒绝。
     */
    private void dropLeadingOrphans() {
        int orphans = MessageGroups.leadingOrphanSize(messages);
        if (orphans > 0) {
            log.info("加载的历史从对话组中间开始，丢弃开头{}条无主消息", orphans);
            messages.subList(0, orphans).clear();
        }
    }

    @Override
    public synchronized void add(Message message) {
        if (message instanceof UserMessage || message instanceof AssistantMessage || message instanceof ToolResponseMessage) {
            messages.add(message);
        } else {
            log.warn("不支持的消息类型：{}",message.getClass().getName());
        }
    }

    /**
     * 返回带系统提示词的消息列表，接受运行时上下文（位置、声纹等）
     */
    public synchronized List<Message> messages(ConversationContext context) {
        // 按对话组裁剪：一组从队首到下一条 UserMessage 之前，工具链不论多长都整组进出，
        // 队首必须始终落在 UserMessage 上，不能留下孤儿 tool_call 或孤儿 ToolResponseMessage
        while (messages.size() > maxMessages + 1) {
            int groupSize = MessageGroups.firstGroupSize(messages);
            // 只剩最后一组时保留整组，宁可超出窗口也不送出残缺的工具链
            if (groupSize >= messages.size()) {
                break;
            }
            for (int i = 0; i < groupSize; i++) {
                messages.remove(0);
            }
        }
        // 新消息列表对象，避免使用过程中污染原始列表对象
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(roleSystemMessage(context));
        historyMessages.addAll(messages);
        // UserMessage 按 metadata 装配带前缀的副本供 LLM 使用
        return historyMessages.stream().map(UserMessageAssembler::assemble).toList();
    }

    @Override
    public synchronized List<Message> messages() {
        return messages(ConversationContext.EMPTY);
    }

}
