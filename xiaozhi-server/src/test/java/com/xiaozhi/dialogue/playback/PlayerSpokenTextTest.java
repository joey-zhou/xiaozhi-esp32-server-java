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
    void stopCallbackFiresOnSendStop() {
        Player player = newPlayer();
        AtomicBoolean stopped = new AtomicBoolean(false);
        player.setOnPlaybackStopped(() -> stopped.set(true));

        player.sendStop();

        assertThat(stopped).isTrue();
        assertThat(player.isPlaying()).isFalse();
    }
}
