package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 两个启动入口的 {@code @ComponentScan} 与 {@code @MapperScan} 是手工清单，新增业务包漏登记要到进程启动时才报错。
 * 本类按依赖闭包核对：入口扫到的每个 bean，它注入的 com.xiaozhi bean 所在包必须也在同一入口的清单里。
 * <p>
 * 每个入口只检查它运行时看得见的模块：xiaozhi-server 对 xiaozhi-dialogue 是 test 依赖、xiaozhi-dialogue 不依赖
 * xiaozhi-server，两个进程各自加载不到对方的类，拿全仓的类去核对会报出一批不存在的问题。
 */
class ComponentScanCoverageArchTest {

    private static final String[] SERVER_RUNTIME_MODULES = {
        "/xiaozhi-common/", "/xiaozhi-service/", "/xiaozhi-ai/", "/xiaozhi-server/"
    };

    private static final String[] DIALOGUE_RUNTIME_MODULES = {
        "/xiaozhi-common/", "/xiaozhi-service/", "/xiaozhi-ai/", "/xiaozhi-dialogue/"
    };

    private static JavaClasses serverRuntimeClasses;
    private static JavaClasses dialogueRuntimeClasses;

    @BeforeAll
    static void importClasses() {
        serverRuntimeClasses = importModules(SERVER_RUNTIME_MODULES);
        dialogueRuntimeClasses = importModules(DIALOGUE_RUNTIME_MODULES);
    }

    private static JavaClasses importModules(String[] modules) {
        ImportOption onlyThoseModules = location ->
            Arrays.stream(modules).anyMatch(m -> location.contains(m + "target/classes/"));
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(onlyThoseModules)
            .importPackages("com.xiaozhi");
    }

    @Test
    void serverEntryPointScansEveryInjectedPackage() {
        assertThat(missingPackages(com.xiaozhi.XiaozhiApplication.class, serverRuntimeClasses))
            .as("XiaozhiApplication 的 @ComponentScan/@MapperScan 漏了这些包，启动时注入会失败")
            .isEmpty();
    }

    @Test
    void dialogueEntryPointScansEveryInjectedPackage() {
        assertThat(missingPackages(com.xiaozhi.DialogueApplication.class, dialogueRuntimeClasses))
            .as("DialogueApplication 的 @ComponentScan/@MapperScan 漏了这些包，dialogue 进程启动时注入会失败")
            .isEmpty();
    }

    /**
     * 清单读不到、或某个模块的类没被导入时，两条覆盖规则会假绿。
     * 逐模块断言在场与缺席，只断言类的数量下限抓不到「模块路径写错导致范围变窄」。
     */
    @Test
    void scanListsAndModulesAreActuallyRead() {
        for (Class<?> entryPoint : List.of(com.xiaozhi.XiaozhiApplication.class, com.xiaozhi.DialogueApplication.class)) {
            assertThat(componentScanPackages(entryPoint))
                .as(entryPoint.getSimpleName() + " 的 @ComponentScan 没读到")
                .hasSizeGreaterThan(5);
            assertThat(mapperScanPackages(entryPoint))
                .as(entryPoint.getSimpleName() + " 的 @MapperScan 没读到")
                .hasSizeGreaterThan(5);
        }

        assertModuleVisible(serverRuntimeClasses, "server", "com.xiaozhi.common", "com.xiaozhi.config", "com.xiaozhi.ai", "com.xiaozhi.server");
        assertModuleAbsent(serverRuntimeClasses, "server", "com.xiaozhi.dialogue");

        assertModuleVisible(dialogueRuntimeClasses, "dialogue", "com.xiaozhi.common", "com.xiaozhi.config", "com.xiaozhi.ai", "com.xiaozhi.dialogue");
        assertModuleAbsent(dialogueRuntimeClasses, "dialogue", "com.xiaozhi.server");
    }

    private static void assertModuleVisible(JavaClasses classes, String runtime, String... markerPackages) {
        for (String marker : markerPackages) {
            assertThat(classes.stream().map(JavaClass::getPackageName))
                .as(runtime + " 运行时没导入 " + marker + " 的类，覆盖规则会漏检")
                .anyMatch(p -> p.equals(marker) || p.startsWith(marker + "."));
        }
    }

    private static void assertModuleAbsent(JavaClasses classes, String runtime, String markerPackage) {
        assertThat(classes.stream().map(JavaClass::getPackageName))
            .as(runtime + " 运行时不该看见 " + markerPackage + " 的类")
            .noneMatch(p -> p.equals(markerPackage) || p.startsWith(markerPackage + "."));
    }

    /** 返回该入口漏扫的包，格式为「缺失包 <- 注入它的 bean」。 */
    private static Set<String> missingPackages(Class<?> entryPoint, JavaClasses runtimeClasses) {
        Set<String> scanned = componentScanPackages(entryPoint);
        Set<String> mapperScanned = mapperScanPackages(entryPoint);
        Set<String> missing = new TreeSet<>();

        for (JavaClass bean : springBeans(runtimeClasses)) {
            if (!covered(bean.getPackageName(), scanned)) {
                continue;
            }
            for (JavaClass dependency : injectedTypes(bean)) {
                for (JavaClass provider : beanProvidersOf(dependency, runtimeClasses)) {
                    Set<String> required = isMapper(provider) ? mapperScanned : scanned;
                    if (!covered(provider.getPackageName(), required)) {
                        missing.add(provider.getPackageName() + " <- " + bean.getSimpleName());
                    }
                }
            }
        }
        return missing;
    }

    private static Set<String> componentScanPackages(Class<?> entryPoint) {
        ComponentScan annotation = entryPoint.getAnnotation(ComponentScan.class);
        return annotation == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(annotation.basePackages()));
    }

    private static Set<String> mapperScanPackages(Class<?> entryPoint) {
        MapperScan annotation = entryPoint.getAnnotation(MapperScan.class);
        return annotation == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(annotation.value()));
    }

    private static boolean covered(String packageName, Set<String> scanned) {
        return scanned.stream().anyMatch(p -> packageName.equals(p) || packageName.startsWith(p + "."));
    }

    private static Set<JavaClass> springBeans(JavaClasses classes) {
        return classes.stream()
            .filter(ComponentScanCoverageArchTest::isSpringBean)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isSpringBean(JavaClass javaClass) {
        return javaClass.isMetaAnnotatedWith(Component.class) || isMapper(javaClass);
    }

    private static boolean isMapper(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(Mapper.class);
    }

    /** 字段与构造器参数里的 com.xiaozhi 类型，Spring 的依赖只可能出现在这两处。 */
    private static List<JavaClass> injectedTypes(JavaClass bean) {
        List<JavaClass> types = new ArrayList<>();
        bean.getFields().forEach(field -> types.add(field.getRawType()));
        bean.getConstructors().forEach(constructor -> types.addAll(constructor.getRawParameterTypes()));
        return types.stream()
            .filter(t -> t.getPackageName().startsWith("com.xiaozhi"))
            .distinct()
            .toList();
    }

    /** 能提供这个类型的 bean：类型本身是 bean 就是它自己，是接口就找该入口看得见的实现类。 */
    private static Set<JavaClass> beanProvidersOf(JavaClass type, JavaClasses runtimeClasses) {
        if (isSpringBean(type)) {
            return Set.of(type);
        }
        if (!type.isInterface()) {
            return Set.of();
        }
        return type.getAllSubclasses().stream()
            .filter(ComponentScanCoverageArchTest::isSpringBean)
            .filter(c -> runtimeClasses.contain(c.getName()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
