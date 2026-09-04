package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块边界约束：反向需求一律经 common/port 接口倒置。
 * <p>
 * 全模块规则靠 xiaozhi-server 的 test 作用域 xiaozhi-dialogue 依赖才扫得到 dialogue 类；
 * 去掉那条依赖或把本类挪出 server 模块，规则照常通过，只是不再覆盖 dialogue。
 */
class ModuleBoundaryArchTest {

    /** communication.auth 的源码在 xiaozhi-common，不在这三个包里 */
    private static final String[] DOWNSTREAM_PACKAGES = {
        "com.xiaozhi.dialogue..", "com.xiaozhi.communication..", "com.xiaozhi.ai.."
    };

    private static final String[] MODULES_BELOW_SERVICE = {
        "com.xiaozhi.ai..", "com.xiaozhi.dialogue..", "com.xiaozhi.server.."
    };

    private static final String[] PROVIDER_SDK_PACKAGES = {
        "org.springframework.ai..", "com.alibaba.dashscope..", "com.aliyun..",
        "com.qcloud..", "software.amazon.."
    };

    /** 生产代码改好后，连同 {@link #knownProviderSdkUsersInServerModule} 一起删。 */
    private static final String SDK_KNOWN_VIOLATIONS =
        "com\\.xiaozhi\\.(config\\.ConfigConnectionChecker"
            + "|server\\.web\\.chat\\.WebChatService"
            + "|utils\\.SmsUtils)";

    /** 路径匹配失效会扫到 0 个类而假绿，用它的规则须先过 {@link #serverModuleIsActuallyScanned}。 */
    private static final ImportOption ONLY_SERVER_MODULE =
        location -> location.contains("/xiaozhi-server/target/classes/");

    /** 同上，用它的规则须先过 {@link #serviceModuleIsActuallyScanned}。 */
    private static final ImportOption ONLY_SERVICE_MODULE =
        location -> location.contains("/xiaozhi-service/target/classes/");

    private static final String[] PACKAGES_ALLOWED_DOMAIN = {
        "com.xiaozhi.config..", "com.xiaozhi.device..", "com.xiaozhi.role.."
    };

    /** common.domain 是领域事件基类型，communication.domain 是协议报文，都不是业务聚合根 */
    private static final String[] NOT_BUSINESS_PACKAGES = {
        "com.xiaozhi.common..", "com.xiaozhi.communication.."
    };

    private static JavaClasses xiaozhiClasses;
    private static JavaClasses serverClasses;
    private static JavaClasses serviceClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
        serverClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ONLY_SERVER_MODULE)
            .importPackages("com.xiaozhi");
        serviceClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ONLY_SERVICE_MODULE)
            .importPackages("com.xiaozhi");
    }

    @Test
    void downstreamModulesDoNotReachIntoDomainRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(DOWNSTREAM_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.repository..")
            .because("聚合根的写路径归 service 模块自己，下游拿到 Repository 会连聚合根一起绕开；实时链路写设备走 common/port/DeviceWriter");

        rule.check(xiaozhiClasses);
    }

    @Test
    void downstreamModulesDoNotReachIntoServiceImplementations() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(DOWNSTREAM_PACKAGES)
            .should().dependOnClassesThat(
                resideInAPackage("..service.impl..").and(resideOutsideOfPackages(DOWNSTREAM_PACKAGES)))
            .because("下游模块只依赖 Service 接口或 common/port，依赖实现类会把事务与缓存细节泄漏到会话链路");

        rule.check(xiaozhiClasses);
    }

    @Test
    void serviceModuleIsActuallyScanned() {
        assertThat(serviceClasses)
            .as("按路径过滤 xiaozhi-service 的产物失效了，依赖它的规则会假绿")
            .hasSizeGreaterThan(80);
    }

    // 挡的不是写错 import——那种 Maven 先编译不过（service 的 pom 里没有 ai/dialogue/server）。
    // 挡的是有人往 xiaozhi-service/pom.xml 里加反向依赖：加完编译能过，依赖链当场成环。
    @Test
    void serviceModuleDoesNotDependOnDownstreamModules() {
        ArchRule rule = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(MODULES_BELOW_SERVICE)
            .because("service 反向依赖下游模块就是把依赖链掰成环；反向需求一律经 common/port 倒置");

        rule.check(serviceClasses);
    }

    @Test
    void serverModuleIsActuallyScanned() {
        assertThat(serverClasses)
            .as("按路径过滤 xiaozhi-server 的产物失效了，依赖它的规则会全部假绿")
            .hasSizeGreaterThan(30);
        assertThat(serverClasses.stream().map(c -> c.getName()))
            .as("过滤把别的模块也带进来了")
            .allMatch(name -> name.startsWith("com.xiaozhi."));
    }

    @Test
    void serverModuleDoesNotDependOnProviderSdk() {
        ArchRule rule = noClasses()
            .that().haveNameNotMatching(SDK_KNOWN_VIOLATIONS)
            .should().dependOnClassesThat()
            .resideInAnyPackage(PROVIDER_SDK_PACKAGES)
            .because("Provider SDK 只归 xiaozhi-ai 用；server 层拿到 SDK 说明编排里混进了模型/云服务细节");

        rule.check(serverClasses);
    }

    @Test
    void onlyWhitelistedPackagesHaveDomainLayer() {
        ArchRule rule = classes()
            .that().resideInAnyPackage("com.xiaozhi..domain..", "com.xiaozhi..infrastructure..")
            .and().resideOutsideOfPackages(NOT_BUSINESS_PACKAGES)
            .should().resideInAnyPackage(PACKAGES_ALLOWED_DOMAIN)
            .because("domain/ + infrastructure/ 要同时满足「有跨字段不变量」与「写入口不止一个」，不命中即禁止新建");

        rule.check(xiaozhiClasses);
    }

    @Test
    void knownProviderSdkUsersInServerModule() {
        ArchRule rule = classes()
            .that().haveNameMatching(SDK_KNOWN_VIOLATIONS)
            .should().dependOnClassesThat()
            .resideInAnyPackage(PROVIDER_SDK_PACKAGES)
            .because("钉住已知违规，防止豁免范围被悄悄扩大");

        rule.check(serverClasses);
    }
}
