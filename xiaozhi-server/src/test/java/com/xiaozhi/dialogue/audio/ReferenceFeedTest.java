package com.xiaozhi.dialogue.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AEC 参考按采集节拍出让：常态 1:1，开播突发几拍内追平，落后回显点时追赶，陈旧帧丢弃。
 */
class ReferenceFeedTest {

    private static byte[] frame(int marker) {
        byte[] pcm = new byte[ReferenceFeed.BLOCKS_PER_FRAME * ReferenceFeed.BLOCK_BYTES];
        Arrays.fill(pcm, (byte) marker);
        return pcm;
    }

    private static boolean isSilence(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static int marker(byte[] block) {
        return block[0];
    }

    @Test
    void oneBlockPerCaptureBlockWithoutBacklog() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));

        for (int i = 0; i < ReferenceFeed.BLOCKS_PER_FRAME; i++) {
            List<byte[]> blocks = feed.blocksForCaptureBlock();
            assertThat(blocks).hasSize(1);
            assertThat(marker(blocks.get(0))).isEqualTo(1);
        }
        List<byte[]> blocks = feed.blocksForCaptureBlock();
        assertThat(blocks).hasSize(1);
        assertThat(isSilence(blocks.get(0))).isTrue();
        assertThat(feed.realBlocks()).isEqualTo(6);
        assertThat(feed.silenceBlocks()).isEqualTo(1);
    }

    @Test
    void keepsTwoFramesOfBacklogAndDrainsExcessWithinFewCaptureBlocks() {
        ReferenceFeed feed = new ReferenceFeed();
        for (int i = 1; i <= 5; i++) {
            feed.add(1000 + i * 60L, frame(i));
        }

        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sizes.add(feed.blocksForCaptureBlock().size());
        }

        assertThat(sizes).containsExactly(4, 4, 4, 4, 4, 1);
        assertThat(feed.catchUpBlocks()).isEqualTo(15);
        assertThat(feed.queuedFrames()).isEqualTo(1);
    }

    @Test
    void startupBurstOfThreeFramesIsDrainedToTwo() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.add(1060, frame(2));
        feed.add(1120, frame(3));

        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sizes.add(feed.blocksForCaptureBlock().size());
        }

        assertThat(sizes).containsExactly(4, 4, 1, 1);
        assertThat(feed.catchUpBlocks()).isEqualTo(6);
    }

    @Test
    void steadyBacklogSwingOfTwoFramesDoesNotTriggerCatchUp() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.blocksForCaptureBlock();
        feed.add(1060, frame(2));
        feed.add(1120, frame(3));
        feed.add(1180, frame(4));

        for (int i = 0; i < 12; i++) {
            assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        }
        assertThat(feed.catchUpBlocks()).isZero();
    }

    @Test
    void zeroBacklogFeedDrainsStartupBurstImmediately() {
        ReferenceFeed feed = new ReferenceFeed(0);
        feed.add(1000, frame(1));
        feed.add(1060, frame(2));
        feed.add(1120, frame(3));

        List<Integer> sizes = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<byte[]> blocks = feed.blocksForCaptureBlock();
            sizes.add(blocks.size());
            blocks.forEach(b -> order.add(marker(b)));
        }

        assertThat(sizes).containsExactly(4, 4, 4, 1);
        assertThat(order).containsExactly(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3);
        assertThat(feed.catchUpBlocks()).isEqualTo(9);
    }

    @Test
    void steadyArrivalDoesNotTriggerCatchUp() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        for (int i = 0; i < 3; i++) {
            assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        }
        feed.add(1060, frame(2));

        for (int i = 0; i < 9; i++) {
            assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        }
        assertThat(feed.catchUpBlocks()).isZero();
        assertThat(feed.silenceBlocks()).isZero();
    }

    @Test
    void catchesUpWhenOneFrameBehindEchoPoint() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.add(1060, frame(2));
        assertThat(feed.blocksForCaptureBlock()).hasSize(1);

        feed.onEchoTimestamp(1060);

        assertThat(feed.blocksForCaptureBlock()).hasSize(1 + ReferenceFeed.CATCH_UP_EXTRA_BLOCKS);
        assertThat(feed.blocksForCaptureBlock()).hasSize(1 + ReferenceFeed.CATCH_UP_EXTRA_BLOCKS);
        assertThat(feed.leadMs()).isZero();
    }

    @Test
    void zeroLeadIsNormalAndDoesNotTriggerCatchUp() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        feed.add(1060, frame(2));

        feed.onEchoTimestamp(1000);

        for (int i = 0; i < 10; i++) {
            assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        }
        assertThat(feed.catchUpBlocks()).isZero();
        assertThat(feed.leadMs()).isEqualTo(60);
    }

    @Test
    void clearForgetsFedTimestampUntilNextFrame() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.blocksForCaptureBlock();
        feed.onEchoTimestamp(1120);

        feed.clear();

        assertThat(feed.leadMs()).isNull();
        feed.add(2000, frame(2));
        assertThat(feed.blocksForCaptureBlock()).hasSize(1);
        assertThat(feed.leadMs()).isEqualTo(880);
    }

    @Test
    void dropsFramesFarBehindEchoPoint() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.add(1060, frame(2));
        feed.add(1700, frame(3));

        feed.onEchoTimestamp(1700);
        List<byte[]> blocks = feed.blocksForCaptureBlock();

        assertThat(marker(blocks.get(0))).isEqualTo(3);
        assertThat(feed.queuedFrames()).isZero();
    }

    @Test
    void leadIsFedTimestampMinusEchoTimestamp() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1120, frame(1));
        feed.blocksForCaptureBlock();
        assertThat(feed.leadMs()).isNull();

        assertThat(feed.onEchoTimestamp(1000)).isTrue();
        assertThat(feed.onEchoTimestamp(1060)).isFalse();

        assertThat(feed.leadMs()).isEqualTo(60);
    }

    @Test
    void timestampDiffSurvivesWrapAround() {
        assertThat(ReferenceFeed.tsDiff(5, 0xFFFFFFFFL)).isEqualTo(6);
        assertThat(ReferenceFeed.tsDiff(0xFFFFFFFFL, 5)).isEqualTo(-6);
        assertThat(ReferenceFeed.tsDiff(1000, 400)).isEqualTo(600);
    }

    @Test
    void clearDropsQueuedAndCurrentFrames() {
        ReferenceFeed feed = new ReferenceFeed();
        feed.add(1000, frame(1));
        feed.add(1060, frame(2));
        feed.blocksForCaptureBlock();

        feed.clear();

        assertThat(feed.queuedFrames()).isZero();
        assertThat(isSilence(feed.blocksForCaptureBlock().get(0))).isTrue();
    }

    @Test
    void keepsAtMostMaxFrames() {
        ReferenceFeed feed = new ReferenceFeed();
        for (int i = 0; i < ReferenceFeed.MAX_FRAMES + 6; i++) {
            feed.add(1000 + i * 60L, frame(1));
        }
        assertThat(feed.queuedFrames()).isEqualTo(ReferenceFeed.MAX_FRAMES);
    }
}
