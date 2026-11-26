package com.xiaozhi.music.service;

import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.exception.OperationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地音乐目录的上传：只收 mp3 与播放列表，按文件头判型；设备端的本地音乐从这个目录放
 */
@Service
public class MusicFileService {

    public static final long MAX_BYTES = 50L * 1024 * 1024;

    public static final String PLAYLIST = "playlist.txt";

    private final RuntimePathConfig runtimePathConfig;

    public MusicFileService(RuntimePathConfig runtimePathConfig) {
        this.runtimePathConfig = runtimePathConfig;
    }

    /**
     * @param originalName 客户端给的文件名，只取最后一段
     * @return 存下的文件名
     * @throws IllegalArgumentException 文件名、类型或大小不合规
     */
    public String save(String originalName, byte[] content) {
        String name = baseName(originalName);
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("文件名无效");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许：" + MAX_BYTES / 1024 / 1024 + "MB");
        }
        if (!PLAYLIST.equals(name)) {
            if (!name.toLowerCase().endsWith(".mp3")) {
                throw new IllegalArgumentException("仅支持 .mp3 或 playlist.txt");
            }
            if (!isMp3(content)) {
                throw new IllegalArgumentException("文件内容不是 mp3");
            }
        }
        Path dir = runtimePathConfig.resolveMusicDir();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件名");
        }
        try {
            Files.createDirectories(dir);
            Files.write(target, content);
        } catch (IOException e) {
            throw new OperationFailedException("上传失败，请稍后重试", e);
        }
        return name;
    }

    /** ID3 标签开头，或直接是 MPEG 帧同步字 */
    private static boolean isMp3(byte[] content) {
        if (content.length >= 3 && content[0] == 'I' && content[1] == 'D' && content[2] == '3') {
            return true;
        }
        return content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xE0) == 0xE0;
    }

    private static String baseName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return null;
        }
        Path fileName = Paths.get(originalName.replace('\\', '/')).getFileName();
        return fileName == null ? null : fileName.toString();
    }
}
