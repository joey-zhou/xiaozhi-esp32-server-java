package com.xiaozhi.dialogue.playback;

import com.xiaozhi.common.Speech;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 打断时历史截到哪，全看播放器记下了哪些句子已经开始下发。
 */
class PlayerSpokenTextTest {

    private static Player newPlayer() {
        return new Player(new WebSocketSession("s1"), mock(MessageSender.class)) {
            @Override
            public void play(Flux<Speech> speechFlux, boolean reply) {
            }
        };
    }

    @Test
    void recordsSentencesInSendOrder() {
        Player player = newPlayer();

        player.sendSentenceStart("第一句。", true);
        player.sendSentenceStart("第二句。", true);

        assertThat(player.spokenText()).isEqualTo("第一句。第二句。");
    }

    @Test
    void nonReplySentencesAreNotRecorded() {
        Player player = newPlayer();

        player.sendSentenceStart("让我看看。", false);
        player.sendSentenceStart("查到了。", true);
        player.sendSentenceStart("你好，我在。", false);

        assertThat(player.spokenText()).isEqualTo("查到了。");
    }

    @Test
    void resetDropsPreviousTurn() {
        Player player = newPlayer();
        player.sendSentenceStart("上一轮。", true);

        player.resetSpokenSentences();
        player.sendSentenceStart("本轮。", true);

        assertThat(player.spokenText()).isEqualTo("本轮。");
    }

    @Test
    void emptyWhenNothingSent() {
        assertThat(newPlayer().spokenText()).isEmpty();
    }

    @Test
    void recentlySpokeMatchesEchoOfOwnSentence() {
        Player player = newPlayer();
        player.sendSentenceStart("等我一下哈", false);
        player.sendSentenceStart("要不要再来一个？", true);

        assertThat(player.recentlySpoke("等我一下哈。")).isTrue();
        assertThat(player.recentlySpoke("要不要再来一个")).isTrue();
        // 短句被包含不算回声，用户可能只是复述了半句来回答
        assertThat(player.recentlySpoke("再来一个")).isFalse();
        assertThat(player.recentlySpoke("给我换一个")).isFalse();
        assertThat(player.recentlySpoke("")).isFalse();
    }

    @Test
    void whilePlayingGarbledEchoIsMatchedBySubsequence() {
        Player player = newPlayer();
        player.setPlaying(true);
        player.sendSentenceStart("欸～听好啰，讲个冷到结冰的：", true);
        player.sendSentenceStart("欸～刚才是不是有哪里不对？", true);

        assertThat(player.recentlySpoke("讲个结冰的。")).isTrue();
        assertThat(player.recentlySpoke("哎，刚才是")).isTrue();
        // 少于 3 字或匹配不足八成不算
        assertThat(player.recentlySpoke("刚才")).isFalse();
        assertThat(player.recentlySpoke("哪里不对，我没听懂")).isFalse();
    }

    @Test
    void subsequenceRuleOnlyAppliesWhilePlaying() {
        Player player = newPlayer();
        player.sendSentenceStart("欸～听好啰，讲个冷到结冰的：", true);

        assertThat(player.recentlySpoke("讲个结冰的。")).isFalse();
    }

    @Test
    void subsequenceLengthCountsInOrderMatches() {
        assertThat(Player.subsequenceLength("讲个结冰的", "欸听好啰讲个冷到结冰的")).isEqualTo(5);
        assertThat(Player.subsequenceLength("哎刚才是", "哎刚才是不是有哪里不对")).isEqualTo(4);
        assertThat(Player.subsequenceLength("abc", "")).isZero();
    }

    @Test
    void stopCallbackFiresOnSendStop() {
        Player player = newPlayer();
        AtomicBoolean stopped = new AtomicBoolean(false);
        player.setOnPlaybackStopped(() -> stopped.set(true));

        player.sendStop();

        assertThat(stopped).isTrue();
        assertThat(player.isPlaying()).isFalse();
    }
}
