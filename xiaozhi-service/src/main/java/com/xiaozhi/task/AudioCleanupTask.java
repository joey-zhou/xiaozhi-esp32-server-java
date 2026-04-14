package com.xiaozhi.task;

import com.xiaozhi.utils.AudioUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

/**
 * 音频文件定时清理任务
 *
 * 凌晨1点：清理超过 retentionDays 天的对话录音目录
 */
@Slf4j
@Component
public class AudioCleanupTask {

    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupExpiredAudio() {
        Path audioDir = Path.of(AudioUtils.AUDIO_PATH);
        if (!Files.exists(audioDir)) {
            return;
        }

        int retentionDays = AudioUtils.AUDIO_RETENTION_DAYS;
        log.info("========== 开始执行音频文件清理任务（保留{}天）==========", retentionDays);
        LocalDate expireDate = LocalDate.now().minusDays(retentionDays);
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
}
