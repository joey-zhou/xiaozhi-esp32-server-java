package com.xiaozhi.storage;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/** 给模型看的图片的上传校验：大小、文件头判型、尺寸（只读头信息，防解压炸弹） */
@Component
public class ImageUploadPolicy {

    public static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 4096L * 4096;

    private static final Map<String, byte[]> MAGICS = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
            "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
            "image/bmp", new byte[]{0x42, 0x4D});

    /**
     * @return 按文件头判出的 MIME 类型
     * @throws IllegalArgumentException 不合规，message 给用户看
     */
    public String mimeTypeOf(byte[] content) {
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片大小超过限制(" + MAX_BYTES / 1024 / 1024 + "MB)");
        }
        String mimeType = MAGICS.entrySet().stream()
                .filter(magic -> startsWith(content, magic.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的图片格式"));
        checkDimensions(content);
        return mimeType;
    }

    private static void checkDimensions(byte[] content) {
        int[] size = readSize(content);
        if (size == null) {
            throw new IllegalArgumentException("无法解析的图片");
        }
        int width = size[0];
        int height = size[1];
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("图片尺寸超过限制(" + width + "x" + height + ")");
        }
    }

    /** 只读头信息取宽高，读不出返回 null */
    private static int[] readSize(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
