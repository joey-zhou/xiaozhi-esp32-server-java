package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.xiaozhi.DialogueApplication;
import com.xiaozhi.XiaozhiApplication;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>
 * 上面这条依赖闭包核对只能顺着「谁注入了谁」往外找，找不到「一个包里的 bean 从来没人注入」的情况——
 * 典型如只对外暴露 HTTP 接口、没有别的 bean 依赖它的 Controller，把它的包从 basePackages 里删掉，
 * 这条核对规则照样通过。{@link #serverEntryPointScansEveryBeanPackage()}/
 * {@link #dialogueEntryPointScansEveryBeanPackage()} 反过来查：runtime 里存在的每一个 bean，
 * 它的包必须被同一入口扫到，不然就要么补扫、要么登记进 {@link #NOT_SCANNED_BY_DESIGN}。
 */
class ComponentScanCoverageArchTest {

    private static final String[] SERVER_RUNTIME_MODULES = {
        "/xiaozhi-common/", "/xiaozhi-service/", "/xiaozhi-ai/", "/xiaozhi-server/"
    };

    private static final String[] DIALOGUE_RUNTIME_MODULES = {
        "/xiaozhi-common/", "/xiaozhi-service/", "/xiaozhi-ai/", "/xiaozhi-dialogue/"
    };

    /**
     * dialogue 进程的 basePackages 是"xiaozhi-service 需要的部分"（见 DialogueApplication 注释），
     * 而 xiaozhi-service 整个模块都在它的 runtime classpath 上，所以这些包里的 bean 对
     * dialogueRuntimeClasses 可见、却不该被扫到。都是纯 HTTP 管理后台功能，dialogue 只处理
     * 设备 WebSocket，不需要：用户登录鉴权、RBAC、操作日志审计、消息模板管理、资源归属校验。
     * TransactionConfig（com.xiaozhi.service）只是给 @Transactional 显式指定主事务管理器，
     * Spring Boot 单数据源下的自动配置已经兜底，不配也能跑。
     * <p>
     * XiaozhiApplication 的 basePackages 对 xiaozhi-service 是"全量"扫描，不需要例外。
     */
    private static final Map<Class<?>, Set<String>> NOT_SCANNED_BY_DESIGN = Map.of(
        XiaozhiApplication.class, Set.of(),
        DialogueApplication.class, Set.of(
            "com.xiaozhi.agent",
            "com.xiaozhi.authrole",
            "com.xiaozhi.authrolepermission",
            "com.xiaozhi.operationlog",
            "com.xiaozhi.permission",
            "com.xiaozhi.security",
            "com.xiaozhi.service",
            "com.xiaozhi.template",
            "com.xiaozhi.user",
            "com.xiaozhi.userauth"
        )
    );

    private static JavaClasses serverRuntimeClasses;
    private static JavaClasses dialogueRuntimeClasses;

    @BeforeAll
    static void importClasses() {
        serverRuntimeClasses = importModules(SERVER_RUNTIME_MODULES);
        dialogueRuntimeClasses = importModules(DIALOGUE_RUNTIME_MODULES);
    }

    private static JavaClasses importModules(String[] modules) {
        ImportOption onlyThoseModules = location ->
            Arrays.stream(modules).anyMatch(m -> location.contains(m + "target/"));
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(onlyThoseModules)
            .importPackages("com.xiaozhi");
    }

    @Test
    void serverEntryPointScansEveryInjectedPackage() {
        assertThat(missingPackages(XiaozhiApplication.class, serverRuntimeClasses))
            .as("XiaozhiApplication 的 @ComponentScan/@MapperScan 漏了这些包，启动时注入会失败")
            .isEmpty();
    }

    @Test
    void dialogueEntryPointScansEveryInjectedPackage() {
        assertThat(missingPackages(DialogueApplication.class, dialogueRuntimeClasses))
            .as("DialogueApplication 的 @ComponentScan/@MapperScan 漏了这些包，dialogue 进程启动时注入会失败")
            .isEmpty();
    }

    @Test
    void serverEntryPointScansEveryBeanPackage() {
        assertThat(unscannedBeanPackages(XiaozhiApplication.class, serverRuntimeClasses))
            .as("XiaozhiApplication 的 @ComponentScan/@MapperScan 漏了这些 bean 所在的包，且未登记进 NOT_SCANNED_BY_DESIGN")
            .isEmpty();
    }

    @Test
    void dialogueEntryPointScansEveryBeanPackage() {
        assertThat(unscannedBeanPackages(DialogueApplication.class, dialogueRuntimeClasses))
            .as("DialogueApplication 的 @ComponentScan/@MapperScan 漏了这些 bean 所在的包，且未登记进 NOT_SCANNED_BY_DESIGN")
            .isEmpty();
    }

    /**
     * 清单读不到、或某个模块的类没被导入时，两条覆盖规则会假绿。
     * 逐模块断言在场与缺席，只断言类的数量下限抓不到「模块路径写错导致范围变窄」。
     */
    @Test
    void scanListsAndModulesAreActuallyRead() {
        for (Class<?> entryPoint : List.of(XiaozhiApplication.class, DialogueApplication.class)) {
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

    /**
     * 返回该入口存在但漏扫的 bean 包：runtime 里的每一个 bean（含主类自己看不见的、没人注入的），
     * 包名既不在 @ComponentScan/@MapperScan 清单里，也没登记进 NOT_SCANNED_BY_DESIGN。
     */
    private static Set<String> unscannedBeanPackages(Class<?> entryPoint, JavaClasses runtimeClasses) {
        Set<String> scanned = componentScanPackages(entryPoint);
        Set<String> mapperScanned = mapperScanPackages(entryPoint);
        Set<String> exceptions = NOT_SCANNED_BY_DESIGN.getOrDefault(entryPoint, Set.of());
        Set<String> unscanned = new TreeSet<>();

        for (JavaClass bean : springBeans(runtimeClasses)) {
            if (bean.isEquivalentTo(entryPoint)) {
                // 启动类本身由 SpringApplication.run 直接注册为 bean，不经过 ComponentScan
                continue;
            }
            String packageName = bean.getPackageName();
            if (covered(packageName, exceptions)) {
                continue;
            }
            Set<String> required = isMapper(bean) ? mapperScanned : scanned;
            if (!covered(packageName, required)) {
                unscanned.add(packageName + " <- " + bean.getSimpleName());
            }
        }
        return unscanned;
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

    /**
     * 字段与构造器参数里的 com.xiaozhi 类型，Spring 的依赖只可能出现在这两处。
     * 用 {@code getAllInvolvedRawTypes()} 而不是裸的 raw type：像 {@code List<OwnershipChecker>}
     * 这种集合注入，裸 raw type 只有 java.util.List，会把真正的依赖类型 OwnershipChecker 漏掉。
     */
    private static List<JavaClass> injectedTypes(JavaClass bean) {
        List<JavaClass> types = new ArrayList<>();
        bean.getFields().forEach(field -> types.addAll(field.getType().getAllInvolvedRawTypes()));
        bean.getConstructors().forEach(constructor ->
            constructor.getParameterTypes().forEach(type -> types.addAll(type.getAllInvolvedRawTypes())));
        return types.stream()
            .filter(t -> t.getPackageName().startsWith("com.xiaozhi"))
            .distinct()
            .toList();
    }

    /**
     * 能提供这个类型的 bean：类型本身是 bean 就是它自己，是接口就找该入口看得见的实现类，
     * 再加上所有把它当返回值的 {@code @Bean} 方法所在的类（如 OwnershipConfig 里那 10 个
     * 直接 new 匿名实现返回的 checker，没有实现类可找，只能从 @Bean 方法反查）。
     */
    private static Set<JavaClass> beanProvidersOf(JavaClass type, JavaClasses runtimeClasses) {
        if (isSpringBean(type)) {
            return Set.of(type);
        }
        Set<JavaClass> providers = new LinkedHashSet<>(beanMethodProvidersOf(type, runtimeClasses));
        if (type.isInterface()) {
            type.getAllSubclasses().stream()
                .filter(ComponentScanCoverageArchTest::isSpringBean)
                .filter(c -> runtimeClasses.contain(c.getName()))
                .forEach(providers::add);
        }
        return providers;
    }

    private static Set<JavaClass> beanMethodProvidersOf(JavaClass type, JavaClasses runtimeClasses) {
        return type.getMethodsWithReturnTypeOfSelf().stream()
            .filter(method -> method.isAnnotatedWith(Bean.class))
            .map(JavaMethod::getOwner)
            .filter(owner -> runtimeClasses.contain(owner.getName()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
