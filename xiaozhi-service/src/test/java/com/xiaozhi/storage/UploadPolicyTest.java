package com.xiaozhi.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 每类上传一处定义：扩展名白名单、按文件头判型、大小上限；不信客户端自报的类型 */
class UploadPolicyTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    private static final byte[] EXE = {'M', 'Z', 0, 0};

    private final UploadPolicy policy = new UploadPolicy();

    @Test
    void categoriesAreLookedUpByTheirKey() {
        assertThat(UploadPolicy.Category.of("avatar")).isEqualTo(UploadPolicy.Category.AVATAR);
        assertThat(UploadPolicy.Category.of("audio")).isEqualTo(UploadPolicy.Category.AUDIO);
        assertThatThrownBy(() -> UploadPolicy.Category.of("../etc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchingContentPasses() {
        policy.check(UploadPolicy.Category.AVATAR, "头像.PNG", 100, PNG);
        policy.check(UploadPolicy.Category.COMMON, "手册.pdf", 100, PDF);
        policy.check(UploadPolicy.Category.COMMON, "表.xlsx", 100, ZIP);
        policy.check(UploadPolicy.Category.COMMON, "说明.md", 100, "# 标题".getBytes());
        policy.check(UploadPolicy.Category.AUDIO, "我.wav", 100, WAV);
        policy.check(UploadPolicy.Category.COMMON, "data.bin", 100, EXE);
    }

    @Test
    void anExtensionOutsideTheCategoryIsRejected() {
        assertThatThrownBy(() -> policy.check(UploadPolicy.Category.AVATAR, "a.svg", 100, "<svg".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".svg");
        assertThatThrownBy(() -> policy.check(UploadPolicy.Category.AVATAR, "noext", 100, PNG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentThatDoesNotMatchTheExtensionIsRejected() {
        assertThatThrownBy(() -> policy.check(UploadPolicy.Category.AVATAR, "a.png", 100, EXE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内容与扩展名不符");
        assertThatThrownBy(() -> policy.check(UploadPolicy.Category.COMMON, "a.txt", 100, new byte[]{'a', 0, 'b'}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachCategoryHasItsOwnSizeLimit() {
        long avatarMax = UploadPolicy.Category.AVATAR.maxBytes();
        assertThatThrownBy(() -> policy.check(UploadPolicy.Category.AVATAR, "a.png", avatarMax, PNG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2MB");
        policy.check(UploadPolicy.Category.AUDIO, "a.wav", avatarMax, WAV);
    }

    /** 新加扩展名时忘了配判型规则会静默放行任意内容 */
    @Test
    void everyAllowedExtensionHasAContentRule() {
        for (UploadPolicy.Category category : UploadPolicy.Category.values()) {
            assertThat(UploadPolicy.signedExtensions()).as(category.key()).containsAll(category.extensions());
        }
    }
}
