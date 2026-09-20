package com.xiaozhi.task;

import com.xiaozhi.message.service.MessageService;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 音频文件定时清理任务
 *
 * 凌晨1点：清理超过 retentionDays 天的本地录音目录
 * 凌晨1点半：删存储层的过期录音并置空 sys_message.audioPath
 *
 * com.xiaozhi.task 同时在 server 与 dialogue 两个入口的 @ComponentScan 清单里，多实例部署时同一秒点会有多个进程触发，
 * 两个方法的加锁方式因此不同：
 * ① 录音清理只扫本节点磁盘、不调 StorageService，各节点各清各的，不加锁。
 *    并发删只会撞上「文件已不存在」，deleteDirectory 内部逐个 catch，不会中断。
 * ② 录音的清理动 DB 与对象存储，必须全局互斥。
 *    云存储模式下 upload 成功即删本地文件，①扫不到，录音要靠 1 点半那半按 audioPath 删。
 */
@Slf4j
@Component
public class AudioCleanupTask {

    /** 分批删除，每批最多处理的记录数 */
    private static final int BATCH_SIZE = 500;

    private static final String MESSAGE_AUDIO_LOCK = "lock:task:message-audio-purge";

    @Resource
    private RuntimePathConfig runtimePathConfig;

    @Resource
    private MessageService messageService;

    @Resource
    private RedissonClient redissonClient;

    // ---- 对话录音清理（凌晨1点）----

    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupExpiredAudio() {
        Path audioDir = runtimePathConfig.resolveAudioDir();
        if (!Files.exists(audioDir)) {
            return;
        }

        int retentionDays = AudioUtils.AUDIO_RETENTION_DAYS;
        log.info("========== 开始执行音频文件清理任务（保留{}天）==========", retentionDays);
        LocalDate expireDate = DateUtils.today().minusDays(retentionDays);
        int deletedDirs = 0;

        try (Stream<Path> dirs = Files.list(audioDir).filter(Files::isDirectory)) {
            for (Path dir : dirs.toList()) {
                String dirName = dir.getFileName().toString();
                try {
                    LocalDate dirDate = LocalDate.parse(dirName, DateTimeFormatter.ISO_LOCAL_DATE);
                    if (!dirDate.isAfter(expireDate)) {
                        AudioUtils.deleteDirectory(dir);
                        deletedDirs++;
                    }
                } catch (DateTimeParseException ignored) {
                    // 非日期格式的目录跳过
                }
            }
        } catch (IOException e) {
            log.error("音频文件清理任务执行失败", e);
        }

        log.info("========== 音频文件清理完成，共清理{}个目录 ==========", deletedDirs);
    }

    // ---- 对话录音清理（凌晨1点半）----

    /**
     * 删存储层的过期录音并置空 sys_message.audioPath。
     * 动的是共享存储与 DB，必须全局互斥；排在本地目录清理之后，本地模式下文件已删，这里只剩置空。
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void purgeExpiredMessageAudio() {
        runExclusively(MESSAGE_AUDIO_LOCK, this::doPurgeExpiredMessageAudio);
    }

    private void doPurgeExpiredMessageAudio() {
        int retentionDays = AudioUtils.AUDIO_RETENTION_DAYS;
        log.info("========== 开始清理过期对话录音（保留{}天）==========", retentionDays);
        int purged = messageService.purgeExpiredAudio(retentionDays, BATCH_SIZE);
        log.info("========== 过期对话录音清理完成，共{}条 ==========", purged);
    }

    /**
     * 抢到锁的实例执行 task，抢不到的直接跳过本轮（定时任务不需要排队补跑）。
     * 不指定租期，由 Redisson 看门狗随线程存活续期，进程崩溃后锁自动过期。
     */
    private void runExclusively(String lockName, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.info("已有实例在执行，跳过本轮: {}", lockName);
                return;
            }
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待任务锁被中断: {}", lockName);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
