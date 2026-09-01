package com.xiaozhi.communication.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * 上传图片安全校验：大小上限 → 魔数白名单 → 实解码取宽高（不全量解码，防解压炸弹）。
 */
@Slf4j
@Component
public class ImageValidator {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 4096L * 4096;

    private static final List<byte[]> IMAGE_MAGICS = List.of(
            new byte[]{(byte) 0xFF, (byte) 0xD8},                                              // JPEG
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},                 // PNG
            new byte[]{0x47, 0x49, 0x46, 0x38},                                                // GIF
            new byte[]{0x42, 0x4D},                                                            // BMP
            new byte[]{0x52, 0x49, 0x46, 0x46}                                                 // RIFF(WEBP)
    );

    /** 校验通过返回 null，否则返回面向用户的错误信息 */
    public String validate(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            return "图片大小超过限制(5MB)";
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            return "图片读取失败";
        }
        if (!matchesImageMagic(data)) {
            return "不支持的图片格式";
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return "无法解析的图片";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    return "图片尺寸超过限制(" + width + "x" + height + ")";
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("图片解析失败: {}", e.getMessage());
            return "无法解析的图片";
        }
        return null;
    }

    private static boolean matchesImageMagic(byte[] data) {
        for (byte[] magic : IMAGE_MAGICS) {
            if (data.length >= magic.length && startsWith(data, magic)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
