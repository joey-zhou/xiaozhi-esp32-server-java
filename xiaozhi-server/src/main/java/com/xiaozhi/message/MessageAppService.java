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
