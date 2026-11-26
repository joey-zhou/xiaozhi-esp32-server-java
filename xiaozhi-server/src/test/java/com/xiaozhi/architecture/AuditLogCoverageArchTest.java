package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.xiaozhi.common.annotation.AuditLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 改库、改文件、给设备发指令的写接口一律带审计；
 * 豁免的逐个登记并写明原因
 */
class AuditLogCoverageArchTest {

    private static final Set<String> NOT_AUDITED_BY_DESIGN = Set.of(
        // 只读试拨，不落库不改文件
        "ConfigController#test",
        // 设备自己调的协议接口
        "DeviceController#otaActivate",
        "VLChatController#vlChat",
        // 对话本身，消息表已有完整记录
        "WebChatController#open",
        "WebChatController#close",
        "WebChatController#stream"
    );

    private static JavaClasses controllers;

    @BeforeAll
    static void importClasses() {
        controllers = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.xiaozhi");
    }

    @Test
    void writeEndpointsAreAudited() {
        assertThat(unaudited()).isEqualTo(new TreeSet<>(NOT_AUDITED_BY_DESIGN));
    }

    @Test
    void writeEndpointsAreActuallyFound() {
        assertThat(writeEndpoints().count()).as("找到的写接口太少，规则会假绿").isGreaterThan(30);
    }

    private static Set<String> unaudited() {
        return writeEndpoints()
                .filter(method -> !method.isAnnotationPresent(AuditLog.class))
                .map(method -> method.getDeclaringClass().getSimpleName() + "#" + method.getName())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Stream<Method> writeEndpoints() {
        return controllers.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .flatMap(javaClass -> Arrays.stream(javaClass.reflect().getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class) || method.isAnnotationPresent(DeleteMapping.class));
    }
}
