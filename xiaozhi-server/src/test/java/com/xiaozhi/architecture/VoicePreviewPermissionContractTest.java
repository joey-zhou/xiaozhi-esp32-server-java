package com.xiaozhi.architecture;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xiaozhi.common.model.req.TestVoiceReq;
import com.xiaozhi.role.RoleController;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 试听按钮的显隐键与后端试听接口要的权限是同一组，对不上时按钮点了必然 403 或该显示的不显示 */
class VoicePreviewPermissionContractTest {

    @Test
    void theFrontendShowsPreviewForTheKeysTheBackendRequires() throws Exception {
        SaCheckPermission required = RoleController.class.getMethod("testAudio", TestVoiceReq.class)
                .getAnnotation(SaCheckPermission.class);

        assertThat(frontendKeys()).containsExactlyInAnyOrder(required.value());
    }

    private static String[] frontendKeys() throws IOException {
        Matcher matcher = Pattern.compile("export const VOICE_PREVIEW_PERMISSIONS = \\[([^\\]]*)]")
                .matcher(Files.readString(frontendConstants()));
        assertThat(matcher.find()).as("前端没有 VOICE_PREVIEW_PERMISSIONS").isTrue();
        return Arrays.stream(matcher.group(1).split(",")).map(key -> key.trim().replace("'", ""))
                .filter(key -> !key.isEmpty()).toArray(String[]::new);
    }

    /** surefire 的工作目录是模块根；从仓库根跑时不用退一级 */
    private static Path frontendConstants() {
        Path fromModule = Paths.get("../web/src/constants/api.ts");
        return Files.isRegularFile(fromModule) ? fromModule : Paths.get("web/src/constants/api.ts");
    }
}
