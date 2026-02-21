package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.temporal.ChronoField.*;

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
    private static final Logger logger = LoggerFactory.getLogger(Conversation.class);

    public static final String MESSAGE_TYPE_USER = "user";
    public static final String MESSAGE_TYPE_ASSISTANT = "assistant";
    public static final AssistantMessage ROLLBACK_MESSAGE = new AssistantMessage("rollback");
    // device, role, sessionId 唯一确定一个Conversation,as key,通过final保持全程的不变性(immutable)
    private final SysDevice device;
    @Getter
    private final SysRole role;
    private final String sessionId;

    protected List<Message> messages = new ArrayList<>();
    public static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .toFormatter();

    /**
     *
     * @param device
     * @param role
     * @param sessionId
     */
    public Conversation(SysDevice device, SysRole role, String sessionId) {
        super(device.getDeviceId(), role.getRoleId(), sessionId);
        // final 属性的规范要求
        Assert.notNull(device, "device must not be null");
        Assert.notNull(role, "role must not be null");
        Assert.notNull(device.getDeviceId(), "deviceId must not be null");
        Assert.notNull(role.getRoleId(), "roleId must not be null");
        Assert.notNull(sessionId, "sessionId must not be null");
        this.device = device;
        this.role = role;
        this.sessionId = sessionId;
    }

    public SysDevice device() {
        return device;
    }
    public SysRole role() {
        return role;
    }

    public String sessionId() {
        return sessionId;
    }

    public Optional<SystemMessage> roleSystemMessage() {
        // 角色描述是在运行过程中不变的，作为第一条系统消息。
        String roleDesc = role().getRoleDesc();
        // 添加设备地址信息到系统提示词中
        String deviceLocation = device().getLocation();

        StringBuilder msgBuilder = new StringBuilder();
        if(StringUtils.hasText(roleDesc)) {
            msgBuilder.append( "角色描述：" ).append(roleDesc).append(System.lineSeparator());
        }
        if (StringUtils.hasText(deviceLocation)) {
            msgBuilder.append("当前位置：").append(deviceLocation)
                    .append("。如果用户提及现在在哪里，则以新地方为准。")
                    .append(System.lineSeparator());
        }
        msgBuilder.append("当前时间：").append(LocalDateTime.now().format(LOCAL_DATE_TIME));
        if(StringUtils.hasText(roleDesc)) {
            var roleMessage = new SystemMessage(msgBuilder.toString());
            return Optional.of(roleMessage);
        }else{
            return Optional.empty();
        }
    }

    @Deprecated
    public static List<Message> buildDatetimeToolMessage() {
        var assistantMessage = AssistantMessage.builder()
                .content("当前日期时间")
                .properties(Map.of())
                .toolCalls(List.of(new AssistantMessage.ToolCall("current_date_time", "function", "current_date_time", "")))
                .build();
        var localDatetime = LocalDateTime.now();
        var currentDateTime = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("current_date_time", "current_date_time", localDatetime.format(ISO_LOCAL_DATE_TIME))))
                .build();
        logger.debug("fake two messages of function calling for datetime:{} \n {}", assistantMessage, currentDateTime);
        return List.of(assistantMessage, currentDateTime);
    }

    /**
     * 当前Conversation的多轮消息列表。
     */
    public List<Message> messages() {
        return messages;
    }

    /**
     * 清理当前Conversation涉及的相关资源，包括缓存的消息列表。
     * 对于某些具体的子类实现，清理也可能是指删除当前Covnersation的消息。
     */
    public void clear(){
        messages.clear();
    }

    public void add(Message message) {

        if(message instanceof UserMessage userMsg){
            messages.add(userMsg);
            return;
        }

        if(message instanceof AssistantMessage assistantMessage){

            if (assistantMessage == Conversation.ROLLBACK_MESSAGE) {
                if (!messages.isEmpty()) {
                    messages.removeLast();
                }
                return;
            }

            // 2. 更新缓存
            messages.add(assistantMessage);
        }
    }

}
