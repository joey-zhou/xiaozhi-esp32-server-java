package com.xiaozhi.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 发布镜像的流水线。
 */
class ImagePublishGuardTest {

    private static final String PUBLIC_REPOSITORY_ONLY = "if: github.repository == 'joey-zhou/xiaozhi-esp32-server-java'";

    @Test
    void imagesArePublishedOnlyFromThePublicRepository() throws IOException {
        // 测试的工作目录是 xiaozhi-server 模块
        Path workflow = Path.of("..", ".github", "workflows", "docker.yml");
        assumeTrue(Files.exists(workflow), "本仓不带发布流水线");

        String yaml = Files.readString(workflow, StandardCharsets.UTF_8);

        assertThat(yaml).contains("push: true");
        assertThat(yaml)
            .as("docker.yml 会向 GHCR 推镜像")
            .contains(PUBLIC_REPOSITORY_ONLY);
    }
}
