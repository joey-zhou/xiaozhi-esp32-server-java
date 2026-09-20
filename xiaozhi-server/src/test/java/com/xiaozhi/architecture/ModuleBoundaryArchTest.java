package com.xiaozhi.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块边界约束：反向需求一律经 common/port 接口倒置。
 * <p>
 * 全模块规则靠 xiaozhi-server 的 test 作用域 xiaozhi-dialogue 依赖才扫得到 dialogue 类；
 * 去掉那条依赖或把本类挪出 server 模块，覆盖 dialogue 的规则会因为「一个类都判不到」
 * 而空真通过。{@link #dialogueModuleIsActuallyImported} 就是钉这件事的哨兵。
 */
class ModuleBoundaryArchTest {

    /** communication.auth 的源码在 xiaozhi-common，不在这三个包里 */
    private static final String[] DOWNSTREAM_PACKAGES = {
        "com.xiaozhi.dialogue..", "com.xiaozhi.communication..", "com.xiaozhi.ai.."
    };

    /**
     * 第三方服务 SDK，不分 AI 与否：AI 能力的归 xiaozhi-ai，对象存储/短信/邮件归 service 业务包，server 一个都不许直接用。
     * gson、caffeine、hutool、jjwt 这类通用库不算，列进来会误伤。{@link ForbiddenDependencyArchTest} 共用这份名单。
     */
    static final String[] THIRD_PARTY_SDK_PACKAGES = {
        // AI 能力
        "org.springframework.ai..", "io.modelcontextprotocol..", "com.alibaba.dashscope..", "com.alibaba.nls..",
        "com.tencent..", "cn.xfyun..", "com.coze..", "com.k2fsa..", "org.vosk..",
        "io.github.imfangs..", "io.github.whitemagic2014..",
        // 云厂商 OpenAPI 与非 AI 外部服务
        "com.aliyun..", "com.aliyuncs..", "com.qcloud..", "software.amazon..", "io.github.biezhi.."
    };

    /** 路径匹配失效会扫到 0 个类而假绿，用它的规则须先过 {@link #serverModuleIsActuallyScanned}。 */
    private static final ImportOption ONLY_SERVER_MODULE =
        location -> location.contains("/xiaozhi-server/target/");

    /** 同上，用它的规则须先过 {@link #serviceModuleIsActuallyScanned}。 */
    private static final ImportOption ONLY_SERVICE_MODULE =
        location -> location.contains("/xiaozhi-service/target/");

    private static final String[] PACKAGES_ALLOWED_DOMAIN = {
        "com.xiaozhi.config..", "com.xiaozhi.device..", "com.xiaozhi.role.."
    };

    /**
     * 只豁免这两个已经存在的领域包，不整包豁免 common.. / communication..——
     * 整包豁免会让这两个包底下新建的任何 domain 或 infrastructure 子包都对
     * {@link #onlyWhitelistedPackagesHaveDomainLayer} 隐形。
     */
    private static final String[] NOT_BUSINESS_PACKAGES = {
        "com.xiaozhi.common.domain..", "com.xiaozhi.communication.domain.."
    };

    /**
     * ai/dialogue/server 三个模块按编译产物物理路径识别，不按包名段判定：
     * communication/utils/monitoring 是横跨 common/service/dialogue/server 的拆分包，
     * 包名枚举永远补不全。用它的规则须先过 {@link #modulesBelowServiceAreActuallyScanned}。
     */
    private static final ImportOption ONLY_MODULES_BELOW_SERVICE = location ->
        location.contains("/xiaozhi-ai/target/")
            || location.contains("/xiaozhi-dialogue/target/")
            || location.contains("/xiaozhi-server/target/");

    /** §7 禁止业务类以 Helper/Util(s)/Manager/Store 结尾，这几个是存量，登记住不许再增 */
    private static final Set<String> LEGACY_FORBIDDEN_SUFFIX_BEANS = Set.of(
        "com.xiaozhi.common.CacheHelper",
        "com.xiaozhi.communication.common.SessionManager"
    );

    private static final DescribedPredicate<JavaClass> HAS_FORBIDDEN_BEAN_SUFFIX = DescribedPredicate.describe(
        "类名以 Helper/Util(s)/Manager/Store 结尾",
        javaClass -> {
            String simpleName = javaClass.getSimpleName();
            return simpleName.endsWith("Helper") || simpleName.endsWith("Util") || simpleName.endsWith("Utils")
                || simpleName.endsWith("Manager") || simpleName.endsWith("Store");
        });

    private static JavaClasses xiaozhiClasses;
    private static JavaClasses serverClasses;
    private static JavaClasses serviceClasses;
    private static JavaClasses modulesBelowServiceClasses;

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
        modulesBelowServiceClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ONLY_MODULES_BELOW_SERVICE)
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
    void downstreamModulesDoNotReachIntoDal() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(DOWNSTREAM_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..dal..")
            .because("Mapper 与 DO 只归 service 模块用；下游模块直接注入 Mapper 会把表结构泄进会话链路，"
                + "读写需求一律经 common/port 倒置（向下用 XxxLookup/XxxWriter）");

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

    /**
     * dialogue 覆盖哨兵。{@link #DOWNSTREAM_PACKAGES} 里的 com.xiaozhi.dialogue 只有靠
     * xiaozhi-server 对 xiaozhi-dialogue 的 test 作用域依赖才进得了 importPackages 的视野。
     * 那条依赖一旦被删掉，本类里所有覆盖 dialogue 的规则会因为判定集为空而全部通过，
     * 违规可以在完全不触发 CI 的情况下进 dialogue 模块——所以先在这里断言它确实被导入了。
     */
    @Test
    void dialogueModuleIsActuallyImported() {
        assertThat(xiaozhiClasses.stream().map(c -> c.getName()))
            .as("扫不到 com.xiaozhi.dialogue 的类，覆盖 dialogue 的规则已经全部退化成空真")
            .anyMatch(name -> name.startsWith("com.xiaozhi.dialogue."));
        assertThat(xiaozhiClasses.stream()
                .filter(c -> c.getName().startsWith("com.xiaozhi.dialogue."))
                .count())
            .as("只扫到零星几个 dialogue 类，说明依赖被裁剪过，覆盖面已经不完整")
            .isGreaterThan(50);
    }

    // 挡的不是写错 import——那种 Maven 先编译不过（service 的 pom 里没有 ai/dialogue/server）。
    // 挡的是有人往 xiaozhi-service/pom.xml 里加反向依赖：加完编译能过，依赖链当场成环。
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
            .should().dependOnClassesThat()
            .resideInAnyPackage(THIRD_PARTY_SDK_PACKAGES)
            .because("第三方服务 SDK 不得进 server；server 层拿到 SDK 说明编排里混进了模型/云服务细节");

        rule.check(serverClasses);
    }

    @Test
    void serverModuleDoesNotDependOnDal() {
        ArchRule rule = noClasses()
            .should().dependOnClassesThat()
            .resideInAPackage("..dal..")
            .because("Mapper 与 DO 只归 service 模块用；server 层拿到它们就绕开了 Service 与 Convert，表结构直接泄到 web 层");

        rule.check(serverClasses);
    }

    /**
     * BaseDO 曾经放在 xiaozhi-common 的 common.model.dataobject 包，包名不含 "dal" 这一段，
     * 上面 {@link #serverModuleDoesNotDependOnDal} 用 "..dal.." 判定，天然扫不到它——
     * 经它中转的 DO 依赖对这条护栏完全隐形（P2-282）。BaseDO 挪进 xiaozhi-service 的
     * common.dal.mysql.dataobject 包后，这里直接复用同一个 resideInAPackage("..dal..") 谓词
     * 跑一遍 BaseDO 本身：位置一旦挪回 common，这条断言先红，不用等真的有 server 类引用它才发现盲区。
     */
    @Test
    void baseDoIsCoveredByDalPackageGuard() {
        JavaClass baseDO = xiaozhiClasses.get("com.xiaozhi.common.dal.mysql.dataobject.BaseDO");
        assertThat(resideInAPackage("..dal..").test(baseDO))
            .as("BaseDO 不在 ..dal.. 判定面内，serverModuleDoesNotDependOnDal 会扫不到经它中转的依赖")
            .isTrue();
    }

    @Test
    void serviceModuleIsActuallyScanned() {
        assertThat(serviceClasses)
            .as("按路径过滤 xiaozhi-service 的产物失效了，依赖它的规则会假绿")
            .hasSizeGreaterThan(100);
    }

    @Test
    void modulesBelowServiceAreActuallyScanned() {
        assertThat(modulesBelowServiceClasses)
            .as("按路径过滤 ai/dialogue/server 产物失效了，serviceModuleDoesNotDependOnDownstreamModules 会假绿")
            .hasSizeGreaterThan(100);
    }

    @Test
    void serviceModuleDoesNotDependOnDownstreamModules() {
        ArchRule rule = noClasses()
            .should().dependOnClassesThat(physicallyResidesIn(modulesBelowServiceClasses))
            .because("service 反向依赖下游模块就是把依赖链掰成环；反向需求一律经 common/port 倒置，"
                + "向下暴露用 XxxLookup/XxxWriter，向上暴露用 XxxClient");

        rule.check(serviceClasses);
    }

    @Test
    void onlyWhitelistedPackagesHaveDomainLayer() {
        ArchRule rule = classes()
            .that().resideInAnyPackage("com.xiaozhi..domain..", "com.xiaozhi..infrastructure..")
            .and().resideOutsideOfPackages(NOT_BUSINESS_PACKAGES)
            .should().resideInAnyPackage(PACKAGES_ALLOWED_DOMAIN)
            .because("domain/ + infrastructure/ 要同时满足「有跨字段不变量」与「写入口不止一个」；"
                + "不命中时规约要求的动作是禁止新建，不是先建了再说");

        rule.check(xiaozhiClasses);
    }

    /**
     * common.domain 是精确豁免，不是扫不到。{@link #NOT_BUSINESS_PACKAGES} 从整包收窄成
     * domain 子包后，common 下别的位置再新建 domain 或 infrastructure 子包会被上面那条规则抓住；
     * 这里钉住被豁免的包本身仍落在规则的选择器里，防止哪天判定面被改窄成连它都匹配不到。
     */
    @Test
    void commonDomainIsCoveredByDomainLayerGuard() {
        JavaClass abstractDomainEvent = xiaozhiClasses.get("com.xiaozhi.common.domain.AbstractDomainEvent");
        assertThat(resideInAnyPackage("com.xiaozhi..domain..", "com.xiaozhi..infrastructure..").test(abstractDomainEvent))
            .as("common.domain 不在 domain/infrastructure 判定面内，onlyWhitelistedPackagesHaveDomainLayer 会看不到它")
            .isTrue();
    }

    @Test
    void businessComponentsDoNotUseForbiddenSuffix() {
        ArchRule rule = classes()
            .that(HAS_FORBIDDEN_BEAN_SUFFIX)
            .and().resideOutsideOfPackage("com.xiaozhi.ai..")
            .should(beRegisteredLegacyForbiddenSuffixBean())
            .because("§7 禁止新增 Helper/Util/Manager/Store 结尾的业务类；"
                + "xiaozhi-ai 内的技术类（如 *Store）按既有约定不受此规则限制");

        rule.check(xiaozhiClasses);
    }

    /** 存量名单只减不增：里面的类被改名或删掉后要同步摘掉，否则名单会变成没人维护的墓碑 */
    @Test
    void legacyForbiddenSuffixBeansStillExist() {
        Set<String> allNames = xiaozhiClasses.stream().map(JavaClass::getName).collect(Collectors.toSet());
        assertThat(allNames)
            .as("LEGACY_FORBIDDEN_SUFFIX_BEANS 里有类已经不存在了，应该从名单里删掉")
            .containsAll(LEGACY_FORBIDDEN_SUFFIX_BEANS);
    }

    /** 按物理编译产物判定依赖目标属于哪个模块，与包名解耦 */
    private static DescribedPredicate<JavaClass> physicallyResidesIn(JavaClasses moduleClasses) {
        Set<String> names = moduleClasses.stream().map(JavaClass::getName).collect(Collectors.toSet());
        return DescribedPredicate.describe("物理产物落在 ai/dialogue/server 模块内", javaClass -> names.contains(javaClass.getName()));
    }

    private static ArchCondition<JavaClass> beRegisteredLegacyForbiddenSuffixBean() {
        return new ArchCondition<>("已登记为存量豁免（LEGACY_FORBIDDEN_SUFFIX_BEANS）") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean springBean = javaClass.isAnnotatedWith(Component.class)
                    || javaClass.isMetaAnnotatedWith(Component.class);
                if (!springBean || LEGACY_FORBIDDEN_SUFFIX_BEANS.contains(javaClass.getName())) {
                    return;
                }
                events.add(SimpleConditionEvent.violated(javaClass,
                    javaClass.getName() + " 以 Helper/Util/Manager/Store 结尾且不在存量名单里，禁止新增该后缀的业务 Bean"));
            }
        };
    }
}
