package com.xiaozhi.task;

import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.utils.AudioUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * com.xiaozhi.task 同时挂在 server 与 dialogue 两个入口的 @ComponentScan 清单里，
 * 多实例部署时同一秒点会有多个进程触发；动共享存储与 DB 的那一半必须全局互斥，
 * 抢不到分布式锁的实例整轮跳过。
 */
@ExtendWith(MockitoExtension.class)
class AudioCleanupTaskTest {

    @Mock
    private MessageService messageService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private final AudioCleanupTask task = new AudioCleanupTask();

    private String originalAudioPath;

    @AfterEach
    void restoreAudioPath() {
        AudioUtils.AUDIO_PATH = originalAudioPath;
    }

    @BeforeEach
    void setUp() {
        originalAudioPath = AudioUtils.AUDIO_PATH;
        ReflectionTestUtils.setField(task, "messageService", messageService);
        ReflectionTestUtils.setField(task, "redissonClient", redissonClient);
    }

    /**
     * 录音清理删的是本节点本地磁盘，多节点各清各的，一旦加回全局锁就只有一个节点清得掉自己的目录。
     */
    @Test
    void cleanupExpiredAudioTakesNoDistributedLock(@TempDir Path audioDir) {
        AudioUtils.AUDIO_PATH = audioDir.toString();

        task.cleanupExpiredAudio();

        verifyNoInteractions(redissonClient);
    }

    @Test
    void cleanupExpiredAudioDeletesOnlyDirectoriesPastRetention(@TempDir Path audioDir) throws IOException {
        AudioUtils.AUDIO_PATH = audioDir.toString();
        int retentionDays = AudioUtils.AUDIO_RETENTION_DAYS;

        Path expired = dateDir(audioDir, LocalDate.now().minusDays(retentionDays + 1));
        Path onBoundary = dateDir(audioDir, LocalDate.now().minusDays(retentionDays));
        Path fresh = dateDir(audioDir, LocalDate.now().minusDays(1));
        Path notADate = Files.createDirectory(audioDir.resolve("cache"));

        task.cleanupExpiredAudio();

        assertThat(expired).doesNotExist();
        // 边界日等于 expireDate，判据是 !isAfter(expireDate)，同样要清掉
        assertThat(onBoundary).doesNotExist();
        assertThat(fresh).exists();
        assertThat(notADate).exists();
    }

    private static Path dateDir(Path parent, LocalDate date) throws IOException {
        Path dir = Files.createDirectory(parent.resolve(date.format(DateTimeFormatter.ISO_LOCAL_DATE)));
        Files.writeString(dir.resolve("a.wav"), "x");
        return dir;
    }

    /**
     * 存储层的录音与 sys_message 是全局共享状态，和本地磁盘清理相反，这一半必须全局互斥。
     */
    @Test
    void purgeExpiredMessageAudioSkipsEntirelyWhenLockIsHeldElsewhere() throws InterruptedException {
        when(redissonClient.getLock("lock:task:message-audio-purge")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);

        task.purgeExpiredMessageAudio();

        verifyNoInteractions(messageService);
        verify(lock, never()).unlock();
    }

    @Test
    void purgeExpiredMessageAudioUsesRetentionDaysAndReleasesLock() throws InterruptedException {
        when(redissonClient.getLock("lock:task:message-audio-purge")).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        task.purgeExpiredMessageAudio();

        // 与本地目录清理共用同一个保留期
        verify(messageService).purgeExpiredAudio(AudioUtils.AUDIO_RETENTION_DAYS, 500);
        verify(lock).unlock();
    }

}
