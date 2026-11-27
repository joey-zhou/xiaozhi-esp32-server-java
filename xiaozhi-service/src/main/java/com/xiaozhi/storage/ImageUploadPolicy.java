package com.xiaozhi.storage;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 给模型看的图片的上传校验：大小、文件头判型、尺寸（只读头信息，防解压炸弹） */
@Component
public class ImageUploadPolicy {

    public static final long MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 4096L * 4096;

    private static final List<String> ACCEPTED = List.of(".jpg", ".png", ".gif", ".bmp");
    private static final Map<String, String> MIME_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".bmp", "image/bmp");

    /**
     * @return 按文件头判出的 MIME 类型
     * @throws IllegalArgumentException 不合规，message 给用户看
     */
    public String mimeTypeOf(byte[] content) {
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片大小超过限制(" + MAX_BYTES / 1024 / 1024 + "MB)");
        }
        String extension = UploadPolicy.detectExtension(content, ACCEPTED);
        if (extension == null) {
            throw new IllegalArgumentException("不支持的图片格式");
        }
        checkDimensions(content);
        return MIME_TYPES.get(extension);
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
}
