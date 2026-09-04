package com.xiaozhi.message.convert;

import com.xiaozhi.common.model.resp.ConversationResp;
import com.xiaozhi.common.model.resp.MessageResp;
import com.xiaozhi.message.model.ConversationProjection;
import com.xiaozhi.message.model.MessageProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

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
            .ignoringFields("messageId")
            .isEqualTo(projection);
        assertThat(resp.getMessageId()).isEqualTo(123456);
    }

    @Test
    void toRespFromConversationProjectionCarriesEveryColumn() {
        ConversationProjection projection = new ConversationProjection();
        projection.setSessionId("s-1");
        projection.setRoleId(3);
        projection.setRoleName("小智");
        projection.setTitle("今天天气怎么样");
        projection.setUpdateTime(new Date(1_757_000_000_000L));

        ConversationResp resp = convert.toResp(projection);

        assertThat(resp).usingRecursiveComparison().isEqualTo(projection);
    }
}
