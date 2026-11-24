package com.xiaozhi.message.convert;

import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.dal.mysql.dataobject.ConversationDO;
import com.xiaozhi.message.model.MessageProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 投影按名映射到 Resp，列别名与 Resp 字段名错一个字就静默为 null。 */
class MessageConvertTest {

    private final MessageConvert convert = new MessageConvertImpl();

    @Test
    void toRespFromMessageProjectionCarriesEveryColumn() {
        MessageProjection projection = new MessageProjection();
        projection.setMessageId(123456L);
        projection.setDeviceId("aa:bb:cc:dd:ee:ff");
        projection.setDeviceName("客厅音箱");
        projection.setSender("assistant");
        projection.setMessage("你好");
        projection.setAudioPath("audio/1.wav");
        projection.setState("1");
        projection.setMessageType("NORMAL");
        projection.setToolCalls("[]");
        projection.setSessionId("s-1");
        projection.setSource("device");
        projection.setRoleId(3);
        projection.setRoleName("小智");
        projection.setCreateTime(LocalDateTime.of(2026, 9, 4, 10, 0));
        projection.setUpdateTime(LocalDateTime.of(2026, 9, 4, 10, 1));

        MessageResp resp = convert.toResp(projection);

        assertThat(resp).usingRecursiveComparison()
            .ignoringFields("messageId", "attachments")
            .isEqualTo(projection);
        assertThat(resp.getMessageId()).isEqualTo(123456L);
    }

    /** metadata 里的附件拆出给前端 */
    @Test
    void attachmentsInTheMetadataReachTheResp() {
        MessageProjection projection = new MessageProjection();
        projection.setMetadata("{\"attachments\":[{\"kind\":\"image\",\"name\":\"花.png\",\"path\":\"uploads/chat/a.png\"}]}");

        MessageResp resp = convert.toResp(projection);

        assertThat(resp.getAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getKind()).isEqualTo("image");
            assertThat(attachment.getName()).isEqualTo("花.png");
            assertThat(attachment.getUrl()).isEqualTo("uploads/chat/a.png");
        });
    }

    @Test
    void messagesWithoutAttachmentsHaveNone() {
        MessageProjection projection = new MessageProjection();
        projection.setMetadata("{\"speaker\":\"小明\"}");

        assertThat(convert.toResp(projection).getAttachments()).isNull();
        assertThat(convert.toResp(new MessageProjection()).getAttachments()).isNull();
    }

    // 会话表的列经 BO 带到 Resp；角色名不在会话表里，留给接口层补
    @Test
    void conversationCarriesEveryColumnThroughToResp() {
        ConversationDO conversation = new ConversationDO();
        conversation.setSessionId("s-1");
        conversation.setUserId(7);
        conversation.setRoleId(3);
        conversation.setTitle("今天天气怎么样");
        conversation.setCreateTime(LocalDateTime.of(2025, 9, 4, 22, 0));
        conversation.setUpdateTime(LocalDateTime.of(2025, 9, 4, 22, 13, 20));

        ConversationResp resp = convert.toResp(convert.toBO(conversation));

        assertThat(resp.getSessionId()).isEqualTo("s-1");
        assertThat(resp.getRoleId()).isEqualTo(3);
        assertThat(resp.getTitle()).isEqualTo("今天天气怎么样");
        assertThat(resp.getUpdateTime()).isEqualTo(conversation.getUpdateTime());
        assertThat(resp.getRoleName()).isNull();
    }
}
