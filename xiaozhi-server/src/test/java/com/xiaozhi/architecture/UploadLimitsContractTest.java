package com.xiaozhi.architecture;

import com.xiaozhi.storage.UploadPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 前端上传前的预检镜像后端 UploadPolicy，两边对不上时前端放行的文件后端会拒 */
class UploadLimitsContractTest {

    @Test
    void theFrontendAcceptsWhatTheBackendAccepts() throws IOException {
        String validators = Files.readString(frontend("src/utils/fileValidators.ts"));

        assertThat(list(validators, "IMAGE_EXTENSIONS")).isEqualTo(UploadPolicy.Category.AVATAR.extensions());
        assertThat(list(validators, "AUDIO_EXTENSIONS")).isEqualTo(UploadPolicy.Category.AUDIO.extensions());
    }

    @Test
    void theFrontendSizeLimitsAreTheBackendOnes() throws IOException {
        String constants = Files.readString(frontend("src/constants/api.ts"));

        assertThat(number(constants, "MAX_IMAGE_SIZE")).isEqualTo(UploadPolicy.Category.AVATAR.maxBytes());
        assertThat(number(constants, "MAX_AUDIO_SIZE")).isEqualTo(UploadPolicy.Category.AUDIO.maxBytes());
    }

    private static List<String> list(String source, String name) {
        Matcher matcher = Pattern.compile("const " + name + " = \\[([^\\]]*)]").matcher(source);
        assertThat(matcher.find()).as("前端没有 " + name).isTrue();
        return Arrays.stream(matcher.group(1).split(",")).map(item -> item.trim().replace("'", ""))
                .filter(item -> !item.isEmpty()).toList();
    }

    private static long number(String source, String name) {
        Matcher matcher = Pattern.compile("export const " + name + " = ([\\d\\s*]+)\\n").matcher(source);
        assertThat(matcher.find()).as("前端没有 " + name).isTrue();
        return Arrays.stream(matcher.group(1).split("\\*")).map(String::trim).mapToLong(Long::parseLong)
                .reduce(1, (a, b) -> a * b);
    }

    /** surefire 的工作目录是模块根；从仓库根跑时不用退一级 */
    private static Path frontend(String file) {
        Path fromModule = Paths.get("../web", file);
        return Files.isRegularFile(fromModule) ? fromModule : Paths.get("web", file);
    }
}
