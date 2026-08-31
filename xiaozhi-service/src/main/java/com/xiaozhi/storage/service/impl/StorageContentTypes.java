package com.xiaozhi.storage.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 根据文件扩展名推断 Content-Type。
 * <p>
 * 云存储的 {@code upload(Path, ...)} 走 InputStream，SDK 不会自动推断 MIME，
 * 若不显式设置，COS/OSS 会以 {@code application/octet-stream} 存储。
 * 浏览器直接打开或 {@code <audio>} 加载时，OGG/Opus 这类需要 seek 到末页
 * granule position 才能得出时长的容器会被判为非媒体，表现为时长 0s、无法播放；
 * WAV 因时长在文件头且浏览器会嗅探 RIFF，octet-stream 也能播，故此前未暴露。
 */
final class StorageContentTypes {

    private StorageContentTypes() {
    }

    static String resolve(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        switch (ext) {
            case "ogg":
            case "opus":
                return "audio/ogg";
            case "wav":
                return "audio/wav";
            case "mp3":
                return "audio/mpeg";
            case "m4a":
                return "audio/mp4";
            case "aac":
                return "audio/aac";
            case "flac":
                return "audio/flac";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                try {
                    String probed = Files.probeContentType(path);
                    return probed != null ? probed : "application/octet-stream";
                } catch (Exception e) {
                    return "application/octet-stream";
                }
        }
    }
}
