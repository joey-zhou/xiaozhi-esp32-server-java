package com.xiaozhi.message;

import com.xiaozhi.common.model.req.ConversationPageReq;
import com.xiaozhi.common.model.req.MessagePageReq;
import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.message.service.MessageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Message 领域应用服务。
 * <p>
 * 职责：编排 Controller → Domain Service 之间的流程，包括：
 * <ul>
 *   <li>Req/Resp ↔ BO 转换</li>
 *   <li>跨领域校验</li>
 * </ul>
 * <p>
 * 注：UserMessage 的元数据（时间戳/说话人/情绪）已由 {@code MessageMetadataBO} 以结构化方式
 * 存在 sys_message.metadata 的 JSON 列中，{@code message} 列本身就是用户裸文本，前端直接展示无需剥离。
 * 投影拼前缀由 {@code Conversation} 层在送 LLM 前按需做。
 */
@Service
public class MessageAppService {

    @Resource
    private MessageService messageService;

    public PageResp<MessageResp> page(MessagePageReq req, Integer userId) {
        MessagePageReq r = req == null ? new MessagePageReq() : req;
        return messageService.page(r.getPageNo(), r.getPageSize(), r.getDeviceId(), r.getDeviceName(),
                r.getSender(), r.getMessageType(), r.getRoleId(), r.getStartTime(), r.getEndTime(),
                userId, r.getSessionId(), r.getSource());
    }

    public PageResp<ConversationResp> conversationPage(ConversationPageReq req, Integer userId) {
        ConversationPageReq r = req == null ? new ConversationPageReq() : req;
        return messageService.conversationPage(r.getPageNo(), r.getPageSize(), userId, r.getRoleId(), r.getSource());
    }

    public void delete(Integer messageId) {
        messageService.delete(messageId);
    }

    public int deleteByDeviceId(String deviceId) {
        return messageService.deleteByDeviceId(deviceId);
    }
}
