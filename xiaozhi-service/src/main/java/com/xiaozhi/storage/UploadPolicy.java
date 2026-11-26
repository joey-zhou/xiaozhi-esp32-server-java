package com.xiaozhi.storage;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 通用上传各类别的校验：扩展名白名单、按文件头判型、大小上限（不含上界）。前端 fileValidators 镜像这里
 */
@Component
public class UploadPolicy {

    /** 判型要看的文件头长度，tar 的标记在 257 字节处 */
    public static final int HEAD_BYTES = 512;

    private static final List<String> IMAGES = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private static final List<String> AUDIOS = List.of(".wav", ".mp3", ".m4a", ".flac", ".ogg", ".opus", ".aac");

    public enum Category {
        AVATAR("avatar", 2, IMAGES),
        IMAGE("image", 2, IMAGES),
        AUDIO("audio", 10, AUDIOS),
        VIDEO("video", 50, List.of(".mp4", ".avi", ".mov", ".mkv", ".webm")),
        COMMON("common", 50, List.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".mp3", ".wav", ".ogg",
                ".opus", ".flac", ".aac", ".m4a", ".mp4", ".avi", ".mov", ".mkv", ".webm", ".pdf", ".doc", ".docx",
                ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".md", ".json", ".xml", ".tif", ".tiff", ".zip",
                ".rar", ".7z", ".tar", ".gz", ".bin", ".hex"));

        private final String key;
        private final long maxBytes;
        private final List<String> extensions;

        Category(String key, int maxMegabytes, List<String> extensions) {
            this.key = key;
            this.maxBytes = maxMegabytes * 1024L * 1024;
            this.extensions = extensions;
        }

        public String key() {
            return key;
        }

        public long maxBytes() {
            return maxBytes;
        }

        public List<String> extensions() {
            return extensions;
        }

        /** @throws IllegalArgumentException 不认识的类别 */
        public static Category of(String key) {
            return Arrays.stream(values()).filter(category -> category.key.equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型分类: " + key));
        }
    }

    private static final Predicate<byte[]> TEXT = head -> {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    };
    private static final Predicate<byte[]> ANY = head -> true;
    private static final Predicate<byte[]> ZIP = startsWith("PK");
    private static final Predicate<byte[]> OLE = startsWith(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    private static final Predicate<byte[]> MP4 = at(4, "ftyp");

    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.ofEntries(
            Map.entry(".jpg", startsWith(0xFF, 0xD8, 0xFF)),
            Map.entry(".jpeg", startsWith(0xFF, 0xD8, 0xFF)),
            Map.entry(".png", startsWith(0x89, 'P', 'N', 'G')),
            Map.entry(".gif", startsWith("GIF8")),
            Map.entry(".webp", startsWith("RIFF").and(at(8, "WEBP"))),
            Map.entry(".bmp", startsWith("BM")),
            Map.entry(".tif", startsWith("II*").or(startsWith("MM"))),
            Map.entry(".tiff", startsWith("II*").or(startsWith("MM"))),
            Map.entry(".wav", startsWith("RIFF").and(at(8, "WAVE"))),
            Map.entry(".mp3", startsWith("ID3").or(head -> head.length >= 2 && u(head[0]) == 0xFF && (u(head[1]) & 0xE0) == 0xE0)),
            Map.entry(".m4a", MP4),
            Map.entry(".flac", startsWith("fLaC")),
            Map.entry(".ogg", startsWith("OggS")),
            Map.entry(".opus", startsWith("OggS")),
            Map.entry(".aac", head -> head.length >= 2 && u(head[0]) == 0xFF && (u(head[1]) & 0xF6) == 0xF0),
            Map.entry(".mp4", MP4),
            Map.entry(".mov", MP4),
            Map.entry(".avi", startsWith("RIFF").and(at(8, "AVI "))),
            Map.entry(".mkv", startsWith(0x1A, 0x45, 0xDF, 0xA3)),
            Map.entry(".webm", startsWith(0x1A, 0x45, 0xDF, 0xA3)),
            Map.entry(".pdf", startsWith("%PDF")),
            Map.entry(".docx", ZIP),
            Map.entry(".xlsx", ZIP),
            Map.entry(".pptx", ZIP),
            Map.entry(".doc", OLE),
            Map.entry(".xls", OLE),
            Map.entry(".ppt", OLE),
            Map.entry(".zip", ZIP),
            Map.entry(".rar", startsWith("Rar!")),
            Map.entry(".7z", startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)),
            Map.entry(".gz", startsWith(0x1F, 0x8B)),
            Map.entry(".tar", at(257, "ustar")),
            Map.entry(".txt", TEXT),
            Map.entry(".md", TEXT),
            Map.entry(".csv", TEXT),
            Map.entry(".json", TEXT),
            Map.entry(".xml", TEXT),
            Map.entry(".hex", TEXT),
            Map.entry(".bin", ANY));

    /**
     * @param head 文件开头至多 {@link #HEAD_BYTES} 字节
     * @throws IllegalArgumentException 不合规，message 给用户看
     */
    public void check(Category category, String fileName, long size, byte[] head) {
        String extension = extensionOf(fileName);
        if (extension == null || !category.extensions().contains(extension)) {
            throw new IllegalArgumentException("不支持的文件扩展名: " + (extension == null ? "无" : extension));
        }
        if (size >= category.maxBytes()) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许：" + category.maxBytes() / 1024 / 1024 + "MB");
        }
        if (!SIGNATURES.getOrDefault(extension, ANY).test(head)) {
            throw new IllegalArgumentException("文件内容与扩展名不符: " + extension);
        }
    }

    /** 小写、带点的扩展名，没有返回 null */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? null : fileName.substring(dot).toLowerCase();
    }

    /** 配了判型规则的扩展名 */
    static Set<String> signedExtensions() {
        return SIGNATURES.keySet();
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static Predicate<byte[]> startsWith(String prefix) {
        return at(0, prefix);
    }

    private static Predicate<byte[]> startsWith(int... prefix) {
        return head -> {
            if (head.length < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if (u(head[i]) != prefix[i]) {
                    return false;
                }
            }
            return true;
        };
    }

    private static Predicate<byte[]> at(int offset, String marker) {
        return head -> {
            if (head.length < offset + marker.length()) {
                return false;
            }
            for (int i = 0; i < marker.length(); i++) {
                if (head[offset + i] != (byte) marker.charAt(i)) {
                    return false;
                }
            }
            return true;
        };
    }
}
