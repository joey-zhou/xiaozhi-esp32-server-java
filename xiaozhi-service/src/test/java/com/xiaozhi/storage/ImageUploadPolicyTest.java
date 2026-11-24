package com.xiaozhi.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 按文件内容判型，限大小与尺寸 */
class ImageUploadPolicyTest {

    private final ImageUploadPolicy policy = new ImageUploadPolicy();

    private static byte[] imageBytes(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void theTypeComesFromTheContent() throws IOException {
        assertThat(policy.mimeTypeOf(imageBytes(64, 48, "jpg"))).isEqualTo("image/jpeg");
        assertThat(policy.mimeTypeOf(imageBytes(64, 48, "png"))).isEqualTo("image/png");
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = new byte[(int) ImageUploadPolicy.MAX_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;

        assertThatThrownBy(() -> policy.mimeTypeOf(big)).hasMessageContaining("大小超过限制");
    }

    @Test
    void rejectsNonImageContent() {
        assertThatThrownBy(() -> policy.mimeTypeOf("not an image".getBytes())).hasMessage("不支持的图片格式");
        // PE 与 ELF 文件头
        assertThatThrownBy(() -> policy.mimeTypeOf(new byte[]{0x4D, 0x5A, 0x00, 0x00})).hasMessage("不支持的图片格式");
        assertThatThrownBy(() -> policy.mimeTypeOf(new byte[]{0x7F, 0x45, 0x4C, 0x46})).hasMessage("不支持的图片格式");
    }

    @Test
    void rejectsCorruptImageWithValidMagic() {
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        assertThatThrownBy(() -> policy.mimeTypeOf(fakePng)).hasMessage("无法解析的图片");
    }

    @Test
    void rejectsOversizedDimensions() throws IOException {
        byte[] wide = imageBytes(5000, 1, "png");

        assertThatThrownBy(() -> policy.mimeTypeOf(wide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尺寸超过限制");
    }
}
