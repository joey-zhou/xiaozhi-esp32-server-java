package com.xiaozhi.ai.llm.memory;

import lombok.Getter;
import org.springframework.ai.chat.messages.*;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Conversation 是一个 对应于 sys_message 表的，但高于 sys_message 的一个抽象实体。
 * deviceID, roleID, sessionID, 实质构成了一次Conversation的全局唯一ID。这个ID必须final 的。
 * 在关系型数据库里，可以将deviceID, roleID, sessionID 建一个组合索引，注意顺序sessionID放在最后。
 * 在图数据库里， conversation label的节点，连接 device节点、role节点。
 * deviceID与roleID本质上不是Conversation的真正属性，而是外键，代表连接的2个对象。
 * 只有sessionID是真正挂在Conversation的属性。
 *
 * Conversation 也不再负责消息的存储持久化。
 *
 */
public class Conversation extends ConversationIdentifier {

    @Getter
    private final String roleDesc;
    @Getter
    private final Integer userId;
    private final String sessionId;

    protected List<Message> messages = new ArrayList<>();

    /**
     * @param ownerId   聊天参与者标识（设备场景: deviceId, Web 场景: userId）
     * @param roleId    角色ID
     * @param sessionId 会话ID
     * @param roleDesc  角色描述（静态，构造时确定）
     * @param userId    用户ID（消息持久化需要）
     */
    public Conversation(String ownerId, Integer roleId, String sessionId, String roleDesc, Integer userId) {
        super(ownerId, roleId, sessionId);
        Assert.notNull(ownerId, "ownerId must not be null");
        Assert.notNull(roleId, "roleId must not be null");
        Assert.notNull(sessionId, "sessionId must not be null");
        this.sessionId = sessionId;
        this.roleDesc = roleDesc;
        this.userId = userId;
    }

    public String sessionId() {
        return sessionId;
    }

    public Optional<SystemMessage> roleSystemMessage(ConversationContext context) {
        StringBuilder msgBuilder = new StringBuilder();
        if(StringUtils.hasText(roleDesc)) {
            msgBuilder.append( "角色描述：" ).append(roleDesc).append(System.lineSeparator());
        }
        String location = context != null ? context.location() : null;
        if (StringUtils.hasText(location)) {
            msgBuilder.append("当前位置：").append(location)
                    .append("。如果用户提及现在在哪里，则以新地方为准。")
                    .append(System.lineSeparator());
        }
        // 逐条消息的元数据（时间戳、说话人、情绪）由 UserMessageAssembler 拼接在每条 UserMessage 前缀里，
        // 不在此处动态渲染，避免 System Prompt 每轮变化导致前缀 KV cache 失效。
        msgBuilder.append(System.lineSeparator())
            .append("用户消息可能以方括号元数据标签开头，顺序固定为：")
            .append(System.lineSeparator())
            .append("  1. [yyyy-MM-ddTHH:mm:ss] 本次消息发送时间（秒级精度，可用于定时任务、时间相对计算）；")
            .append(System.lineSeparator())
            .append("  2. [情绪标签]（如 [neutral]、[happy]）语音识别出的用户情绪，据此调整回应语气。")
            .append(System.lineSeparator())
            .append("请据此调整回应方式和语气，但无需在回复中提及或解释这些标签。任一标签可能缺省。")
            .append(System.lineSeparator());
        if(StringUtils.hasText(roleDesc)) {
            var roleMessage = new SystemMessage(msgBuilder.toString());
            return Optional.of(roleMessage);
        }else{
            return Optional.empty();
        }
    }

    /**
     * 带运行时上下文的消息列表（子类覆写此方法以注入系统提示词）。
     * <p>
     * 对每条消息走一次 {@link UserMessageAssembler#assemble(Message)}：
     * UserMessage 按其 metadata 装配带前缀的副本送给 LLM，非 UserMessage 原样透传。
     * in-memory 的消息始终是"裸文本 + 结构化 metadata"。
     */
    public synchronized List<Message> messages(ConversationContext context) {
        return messages.stream().map(UserMessageAssembler::assemble).toList();
    }

    /**
     * 当前Conversation的多轮消息列表。
     */
    public synchronized List<Message> messages() {
        return messages(ConversationContext.EMPTY);
    }

    /**
     * 返回原始消息列表（不触发 RAG 检索等副作用）。
     * 用于工具路由的 FC 上下文检测等只需读取历史的场景。
     * 子类（如 RetrievalConversation）可覆写以返回底层 conversation 的原始消息。
     */
    public synchronized List<Message> rawMessages() {
        return messages;
    }

    /**
     * 清理当前Conversation涉及的相关资源，包括缓存的消息列表。
     * 对于某些具体的子类实现，清理也可能是指删除当前Covnersation的消息。
     */
    public synchronized void clear(){
        messages.clear();
    }

    public synchronized void add(Message message) {

        if(message instanceof UserMessage userMsg){
            messages.add(userMsg);
            return;
        }

        if(message instanceof AssistantMessage assistantMessage){
            messages.add(assistantMessage);
            return;
        }

        if(message instanceof ToolResponseMessage toolResponseMessage){
            messages.add(toolResponseMessage);
        }
    }

    /**
     * 将工具调用链（模型的 tool_call 请求 + 工具执行结果）作为原子操作添加到消息列表
     */
    public synchronized void addToolCallChain(AssistantMessage toolCallMsg, ToolResponseMessage toolResponse) {
        messages.add(toolCallMsg);
        messages.add(toolResponse);
    }

}
