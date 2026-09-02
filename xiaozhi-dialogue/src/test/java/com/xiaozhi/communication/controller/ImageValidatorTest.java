package com.xiaozhi.communication.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设备上传的图片只按真实内容判定：光看后缀或 Content-Type 会让可执行文件、
 * 伪造魔数的损坏文件混进来，超大尺寸的图还会把视觉模型的调用打爆。
 */
class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    private static byte[] imageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static MockMultipartFile file(byte[] data) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", data);
    }

    @Test
    void acceptsValidJpegAndPng() throws IOException {
        assertThat(validator.validate(file(imageBytes(64, 48, "jpg")))).isNull();
        assertThat(validator.validate(file(imageBytes(64, 48, "png")))).isNull();
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = new byte[6 * 1024 * 1024];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;

        assertThat(validator.validate(file(big))).contains("大小超过限制");
    }

    @Test
    void rejectsNonImageContent() {
        assertThat(validator.validate(file("not an image".getBytes()))).isEqualTo("不支持的图片格式");
    }

    @Test
    void rejectsExecutableContent() {
        // PE 文件头 MZ
        assertThat(validator.validate(file(new byte[]{0x4D, 0x5A, 0x00, 0x00}))).isEqualTo("不支持的图片格式");
        // ELF 文件头
        assertThat(validator.validate(file(new byte[]{0x7F, 0x45, 0x4C, 0x46}))).isEqualTo("不支持的图片格式");
    }

    @Test
    void rejectsCorruptImageWithValidMagic() {
        byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        assertThat(validator.validate(file(fakePng))).isEqualTo("无法解析的图片");
    }

    @Test
    void rejectsOversizedDimensions() throws IOException {
        assertThat(validator.validate(file(imageBytes(5000, 1, "png")))).contains("尺寸超过限制");
    }
}
