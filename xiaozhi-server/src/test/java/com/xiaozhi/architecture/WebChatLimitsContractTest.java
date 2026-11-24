package com.xiaozhi.architecture;

import com.xiaozhi.ai.tool.XiaoZhiToolCallingManager;
import com.xiaozhi.common.model.req.ChatStreamReq;
import com.xiaozhi.storage.ImageUploadPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 网页聊天在前端 constants/api.ts 镜像的后端取值，两边对不上时前端先放行、后端再拒绝 */
class WebChatLimitsContractTest {

    @Test
    void attachmentLimitsMatchTheBackend() throws IOException {
        String constants = Files.readString(frontendConstants());

        assertThat(number(constants, "CHAT_ATTACHMENT_MAX_COUNT")).isEqualTo(ChatStreamReq.MAX_ATTACHMENTS);
        assertThat(number(constants, "CHAT_IMAGE_MAX_BYTES")).isEqualTo(ImageUploadPolicy.MAX_BYTES);
        assertThat(number(constants, "CHAT_IMAGE_MAX_EDGE")).isLessThanOrEqualTo(ImageUploadPolicy.MAX_DIMENSION);
    }

    /** 工具执行期间流上可以一直不出字，工具结束后还有一段静默上限，前端不能先于后端放弃 */
    @Test
    void theStreamIdleTimeoutOutlastsTheBackend() throws IOException {
        String constants = Files.readString(frontendConstants());
        long backendMillis = XiaoZhiToolCallingManager.TOOL_CALL_TIMEOUT.toMillis() + idleTimeoutSeconds() * 1000;

        assertThat(number(constants, "CHAT_STREAM_IDLE_TIMEOUT_MS")).isGreaterThan(backendMillis);
    }

    /** 取 `export const NAME = 5 * 1024` 这类只有乘法的数值 */
    private static long number(String source, String name) {
        Matcher matcher = Pattern.compile("export const " + name + " = ([\\d\\s*]+)\\n").matcher(source);
        assertThat(matcher.find()).as("前端没有 " + name).isTrue();
        return Arrays.stream(matcher.group(1).split("\\*")).map(String::trim).mapToLong(Long::parseLong)
                .reduce(1, (a, b) -> a * b);
    }

    private static long idleTimeoutSeconds() throws IOException {
        try (InputStream yml = WebChatLimitsContractTest.class.getResourceAsStream("/application-common.yml")) {
            assertThat(yml).as("找不到 application-common.yml").isNotNull();
            Matcher matcher = Pattern.compile("idle-timeout-seconds: (\\d+)")
                    .matcher(new String(yml.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(matcher.find()).as("application-common.yml 里没有 idle-timeout-seconds").isTrue();
            return Long.parseLong(matcher.group(1));
        }
    }

    /** surefire 的工作目录是模块根；从仓库根跑时不用退一级 */
    private static Path frontendConstants() {
        Path fromModule = Paths.get("../web/src/constants/api.ts");
        return Files.isRegularFile(fromModule) ? fromModule : Paths.get("web/src/constants/api.ts");
    }
}
