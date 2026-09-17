package com.xiaozhi.summary.convert;

import com.xiaozhi.common.model.bo.SummaryBO;
import com.xiaozhi.common.model.resp.SummaryResp;
import com.xiaozhi.summary.dal.mysql.dataobject.SummaryDO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SummaryDO 用 LocalDateTime（库里的墙钟），SummaryBO 用 Instant（绝对时刻），
 * 两边靠转换器里的 map() 按系统时区互转。写错方向或漏掉一侧，摘要的
 * lastMessageTimestamp 会整体偏移，续接摘要时会把已经总结过的消息重新总结一遍。
 */
class SummaryConvertTest {

    private final SummaryConvert convert = Mappers.getMapper(SummaryConvert.class);

    @Test
    void localDateTimeAndInstantRoundTripThroughTheSystemZone() {
        LocalDateTime wallClock = LocalDateTime.of(2026, 9, 9, 20, 15, 30);
        SummaryDO source = new SummaryDO();
        source.setDeviceId("dev-1");
        source.setRoleId(3);
        source.setSummary("聊了咖啡");
        source.setPromptTokens(100);
        source.setCompletionTokens(20);
        source.setLastMessageTimestamp(wallClock);
        source.setCreateTime(wallClock);

        SummaryBO bo = convert.toBO(source);

        Instant expected = wallClock.atZone(ZoneId.systemDefault()).toInstant();
        assertThat(bo.getLastMessageTimestamp()).isEqualTo(expected);
        assertThat(bo.getCreateTime()).isEqualTo(expected);
        assertThat(bo.getSummary()).isEqualTo("聊了咖啡");
        assertThat(bo.getPromptTokens()).isEqualTo(100);
        assertThat(bo.getCompletionTokens()).isEqualTo(20);

        SummaryDO back = convert.toDO(bo);
        assertThat(back.getLastMessageTimestamp())
            .as("往返一圈必须回到同一个墙钟，偏移会让续接摘要重算已总结过的消息")
            .isEqualTo(wallClock);
        assertThat(back.getCreateTime()).isEqualTo(wallClock);
    }

    @Test
    void nullTimestampsStayNull() {
        SummaryBO bo = convert.toBO(new SummaryDO());

        assertThat(bo.getLastMessageTimestamp()).isNull();
        assertThat(bo.getCreateTime()).isNull();
    }

    @Test
    void respKeepsTheInstantAsIs() {
        Instant timestamp = Instant.parse("2026-09-09T12:15:30Z");
        SummaryBO bo = new SummaryBO();
        bo.setDeviceId("dev-1");
        bo.setSummary("聊了咖啡");
        bo.setLastMessageTimestamp(timestamp);

        SummaryResp resp = convert.toResp(bo);

        assertThat(resp.getSummary()).isEqualTo("聊了咖啡");
        assertThat(resp.getLastMessageTimestamp()).isEqualTo(timestamp);
    }
}
