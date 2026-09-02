package com.xiaozhi.ai.llm.memory;

import lombok.Getter;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
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
    /** 角色系统提示词模板，占位符 $role_section$、$location_line$ */
    private static final PromptTemplate ROLE_SYSTEM_PROMPT_TEMPLATE = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('$').endDelimiterToken('$').build())
            .resource(new ClassPathResource("/prompts/role_system_prompt.md", Conversation.class))
            .build();

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
     * 返回原始消息列表（不触发任何投影副作用，文本保持"裸文本"，metadata 未拼前缀）。
     * 用于工具路由的 FC 上下文检测。
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

    /**
     * 用截断后的消息替换原消息（按对象身份定位），原消息不在列表里则不做任何事
     */
    public synchronized void replace(Message original, Message replacement) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) == original) {
                messages.set(i, replacement);
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

}
