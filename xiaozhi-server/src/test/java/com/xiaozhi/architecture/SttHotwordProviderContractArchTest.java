package com.xiaozhi.architecture;

import com.xiaozhi.ai.stt.Hotword;
import com.xiaozhi.ai.stt.SttService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 「哪些语音识别服务支持热词」这件事写在两个地方：Java 侧看 provider 有没有覆写
 * {@code SttService.stream(Flux, Consumer, List)}，前端侧看 {@code HOTWORD_PROVIDERS} 决定
 * 显不显示热词输入框。两边各改各的编译器一个字都拦不住：
 * 后端新接一家但前端没加，用户永远看不到输入框；前端多写一家但后端没覆写，
 * 用户填完热词毫无作用且没有任何报错。只能靠这条把两份名单钉在一起。
 */
class SttHotwordProviderContractArchTest {

    /** 匹配前端 HOTWORD_PROVIDERS 数组字面量 */
    private static final Pattern FRONTEND_ARRAY =
            Pattern.compile("HOTWORD_PROVIDERS\\s*=\\s*\\[([^\\]]*)]");
    private static final Pattern QUOTED_ITEM = Pattern.compile("'([^']+)'");

    // 镜像构建只拷后端源码，没有前端可对；前端目录在而文件挪走了仍然要红
    @BeforeEach
    void frontendSourcesArePresent() {
        assumeTrue(Files.isDirectory(Paths.get("../web")) || Files.isDirectory(Paths.get("web")),
                "没有前端源码，跳过前后端热词清单的对账");
    }

    @Test
    void providersOverridingTheHotwordOverloadMatchTheFrontendList() {
        assertThat(backendHotwordProviders()).isEqualTo(frontendHotwordProviders());
    }

    @Test
    void frontendListIsActuallyReadable() {
        // 前端文件挪走或数组改名时这条先红，否则上面那条会拿两个空集合假绿
        assertThat(frontendHotwordProviders()).isNotEmpty();
    }

    /** 覆写了带热词重载的 provider，取其 getProviderName() 返回值 */
    private static Set<String> backendHotwordProviders() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.xiaozhi.ai.stt.providers");
        Set<String> names = new TreeSet<>();
        for (var javaClass : classes) {
            Class<?> type = javaClass.reflect();
            if (!SttService.class.isAssignableFrom(type) || type.isInterface()) {
                continue;
            }
            if (declaresHotwordOverload(type)) {
                names.add(providerNameOf(type));
            }
        }
        return names;
    }

    private static boolean declaresHotwordOverload(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("stream", Flux.class, Consumer.class, List.class);
            return method.getGenericParameterTypes()[2].getTypeName().contains(Hotword.class.getName());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** provider 名是实例方法返回的常量，本地 provider 的构造要连模型，只能读字段 */
    private static String providerNameOf(Class<?> type) {
        try {
            var field = type.getDeclaredField("PROVIDER_NAME");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(type.getSimpleName() + " 没有 PROVIDER_NAME 常量，无法比对名单", e);
        }
    }

    private static Set<String> frontendHotwordProviders() {
        String source = read(roleManagerSource());
        Matcher array = FRONTEND_ARRAY.matcher(source);
        if (!array.find()) {
            return Set.of();
        }
        Set<String> names = new TreeSet<>();
        Matcher item = QUOTED_ITEM.matcher(array.group(1));
        while (item.find()) {
            names.add(item.group(1));
        }
        return names;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** surefire 的工作目录是模块根；从仓库根跑时不用退一级 */
    private static Path roleManagerSource() {
        Path fromModule = Paths.get("../web/src/composables/useRoleManager.ts");
        return Files.isRegularFile(fromModule)
                ? fromModule
                : Paths.get("web/src/composables/useRoleManager.ts");
    }
}
