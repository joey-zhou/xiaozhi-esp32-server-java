package com.xiaozhi.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.xiaozhi.config.domain.repository.ConfigRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规约 §7「可直接写成 ArchUnit 的禁止项」里第 2、3、6、7 条，外加 §4 对 common/port 的签名约束
 * 与 §1 对 server 直连基础设施的约束。第 1、4、5、8、9、11 条在 {@link ServiceLayerArchTest}
 * 与 {@link ModuleBoundaryArchTest}，第 10 条在 {@link MapperXmlArchTest}。
 * <p>
 * 存量违规一律显式列进 KNOWN_VIOLATIONS 并写明原因，不用注释豁免，也不放宽通用规则——
 * 规则要能拦住新增的同类写法，这是补这几条的全部意义。
 * <p>
 * 自定义 {@link ArchCondition} 一律配 {@code classes().should(不满足条件)} 使用，不能写成
 * {@code noClasses().should(满足条件)}：noClasses 会把条件整体取反，而只发 violated 事件的条件
 * 取反后永远没有违规，规则会假绿——这个坑放跑过一次真实故障，见 CacheableReturnTypeArchTest。
 */
class ForbiddenDependencyArchTest {

    /** 判定口径与 {@link ModuleBoundaryArchTest} 保持一致 */
    private static final String[] PROVIDER_SDK_PACKAGES = {
        "org.springframework.ai..", "com.alibaba.dashscope..", "com.aliyun..",
        "com.qcloud..", "software.amazon.."
    };

    /** §4：common/port 是跨模块的倒置接口，签名上出现 Provider SDK 类型等于把 SDK 依赖钉进所有消费方。 */
    private static final Set<String> PORT_SDK_KNOWN_VIOLATIONS = Set.of();

    /**
     * §1：server 是编排层，Redis 这类基础设施应经 common 的抽象访问。
     * 这里存的是只在 server 侧存在的运行态数据（限流计数），
     * 目前没有对应的 common 抽象；登记在此是为了让「又多一个类直连 Redis」能被拦下。
     */
    private static final Set<String> SERVER_REDIS_KNOWN_VIOLATIONS = Set.of(
        "com.xiaozhi.server.config.RateLimitInterceptor"
    );

    /**
     * §7 禁止项 3 的存量：领域事件基类刻意继承 Spring 的 ApplicationEvent，
     * 为的是让所有领域事件直接走 Spring 的事件分发，不再自建一套广播。
     * 这是 common/domain 里唯一一处、且只影响事件基类本身，聚合根不受牵连。
     */
    private static final Set<String> DOMAIN_SPRING_KNOWN_VIOLATIONS = Set.of(
        "com.xiaozhi.common.domain.AbstractDomainEvent"
    );

    /**
     * §6「聚合根 → BO 归 infrastructure/convert 的 XxxConverter」的存量：
     * {@code AiConfig.mergePatch(patch)} 不是自身字段快照，而是「patch 覆盖自身、缺的补自身」的
     * 合并判定（只认 null 为未提供），{@code ConfigTestAppService.resolveConfigUnderTest} 靠它
     * 决定本次外呼用哪份凭据。合并规则本身是领域语义，搬进 Convert 等于把它变成字段拷贝。
     * 快照用途的那个调用方已经去掉，这里只剩这一处。
     */
    private static final Set<String> DOMAIN_TRANSPORT_KNOWN_VIOLATIONS = Set.of(
        "com.xiaozhi.config.domain.AiConfig.mergePatch(com.xiaozhi.common.model.bo.ConfigBO)"
    );

    /** 路径匹配失效会扫到 0 个类而假绿，用它的规则须先过 {@link #serverModuleIsActuallyScanned}。 */
    private static final ImportOption ONLY_SERVER_MODULE =
        location -> location.contains("/xiaozhi-server/target/classes/");

    private static JavaClasses xiaozhiClasses;
    private static JavaClasses serverClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
        serverClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ONLY_SERVER_MODULE)
            .importPackages("com.xiaozhi");
    }

    // ==================== §7 禁止项 2 ====================

    @Test
    void convertersHoldNoInjectedFields() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..convert..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("jakarta.annotation.Resource")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
            .because("转换器只做字段搬运，一旦持有注入依赖就会顺手在转换里查库/调服务，映射关系不再自洽");

        rule.check(xiaozhiClasses);
    }

    @Test
    void convertersDoNotReachPersistenceOrServices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..convert..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..mapper..", "..repository..", "..service..")
            .because("转换器依赖 Mapper/Repository/Service 就变成隐式读路径，调用方看不出一次转换会打几次库");

        rule.check(xiaozhiClasses);
    }

    // ==================== §7 禁止项 3 ====================

    @Test
    void domainLayerStaysFrameworkAndPersistenceFree() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .and(not(hasNameIn(DOMAIN_SPRING_KNOWN_VIOLATIONS)))
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .orShould().dependOnClassesThat().resideInAPackage("..dal..")
            .because("领域层是纯业务规则，依赖 Spring 或 DAL 会让聚合根无法脱离容器与数据库被测试");

        rule.check(xiaozhiClasses);
    }

    // ==================== §6 转换层边界 ====================

    /**
     * 聚合根不得自己产出 BO / Resp。
     * <p>这类「快照方法」是纯字段搬运，归 {@code infrastructure/convert/XxxConverter}（聚合根 → BO）
     * 与 {@code convert/XxxConvert}（→ Resp）。放在聚合根上会让领域层反向依赖传输对象，
     * 且同一份字段清单在两处各存一遍，迟早对不上。
     * <p>反方向不禁：§2 明写 BO 的用途之一就是写侧入参，{@code newConfig(userId, bo)} /
     * {@code update(bo)} 这类接收 BO 的构造与变更方法是规约认可的写法。
     */
    @Test
    void domainDoesNotProduceTransportObjects() {
        ArchRule rule = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and(not(hasFullNameIn(DOMAIN_TRANSPORT_KNOWN_VIOLATIONS)))
            .should().haveRawReturnType(
                resideInAnyPackage("..common.model.bo..", "..common.model.resp.."))
            .because("聚合根出 BO/Resp 是 Converter 的活，领域层不得反向依赖传输对象");

        rule.check(xiaozhiClasses);
    }

    // ==================== §7 禁止项 6 ====================

    @Test
    void controllersDoNotReachPersistenceDirectly() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("..dal..")
            .orShould().dependOnClassesThat().resideInAPackage("..domain.repository..")
            .because("Controller 直连 DAL/Repository 会绕过 AppService 的编排与事务边界");

        rule.check(xiaozhiClasses);
    }

    // ==================== §7 禁止项 7 ====================

    @Test
    void repositoriesAreOnlyUsedInsideTheirOwnBusinessPackage() {
        ArchRule rule = classes()
            .should(notDependOnForeignRepository())
            .because("Repository 是某个聚合的写入口，跨业务包使用等于让别的领域直接改这个聚合的库表");

        rule.check(xiaozhiClasses);
    }

    /**
     * 规则本身要能真的抓到人。生产代码已无存量违规（agent 组装列表改走 ConfigService），
     * 改用一个故意跨包持有 Repository 的样板类来验证：规则对它必须判违规。
     * 样板类是测试类，不在 {@link #xiaozhiClasses} 的判定面里。
     */
    @Test
    void foreignRepositoryRuleActuallyCatchesAViolation() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ForeignRepositoryHolder.class);

        assertThat(classes().should(notDependOnForeignRepository()).evaluate(fixture).hasViolation())
            .as("样板类跨包持有 config 的 Repository 却没被判违规，说明规则根本没在判定，等于假绿")
            .isTrue();
    }

    /** 上面那条反向断言的样板：住在 architecture 包，却持有 config 领域的 Repository */
    @SuppressWarnings("unused")
    private static final class ForeignRepositoryHolder {
        private ConfigRepository configRepository;
    }

    // ==================== §4：common/port 不得出现第三方服务 SDK 类型 ====================

    @Test
    void portsDoNotExposeProviderSdkTypes() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.xiaozhi.common.port")
            .and(not(hasNameIn(PORT_SDK_KNOWN_VIOLATIONS)))
            .should().dependOnClassesThat().resideInAnyPackage(ModuleBoundaryArchTest.THIRD_PARTY_SDK_PACKAGES)
            .because("common/port 是倒置接口，签名带 SDK 类型会把 Provider 依赖传染给每一个消费模块");

        rule.check(xiaozhiClasses);
    }

    // ==================== §1：server 不直连 Redis ====================

    @Test
    void serverModuleDoesNotTalkToRedisDirectly() {
        ArchRule rule = noClasses()
            .that(not(hasNameIn(SERVER_REDIS_KNOWN_VIOLATIONS)))
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data.redis..", "org.redisson..")
            .because("server 是编排层，Redis 访问应经 common 的抽象，直连会让缓存 key 与序列化口径散落在编排代码里");

        rule.check(serverClasses);
    }

    @Test
    void serverModuleIsActuallyScanned() {
        assertThat(serverClasses)
            .as("按路径过滤 xiaozhi-server 的产物失效了，依赖它的规则会全部假绿")
            .hasSizeGreaterThan(40);
        assertThat(serverClasses.stream().map(JavaClass::getName))
            .as("过滤把别的模块也带进来了")
            .allMatch(name -> name.startsWith("com.xiaozhi."));
    }

    // ==================== 判定实现 ====================

    /**
     * 「跨业务包使用 Repository」的判定：业务包取 {@code com.xiaozhi.} 之后的第一段，
     * 使用方与 Repository 接口的第一段不同即算违规。同段内的 AppService、Service、
     * infrastructure 实现都是正常用法。
     */
    private static ArchCondition<JavaClass> notDependOnForeignRepository() {
        return new ArchCondition<>("不跨业务包使用其它领域的 Repository") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownPackage = businessPackageOf(item.getName());
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getPackageName().endsWith(".domain.repository")) {
                        continue;
                    }
                    String targetPackage = businessPackageOf(target.getName());
                    if (ownPackage.equals(targetPackage)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(item,
                        item.getName() + " 使用了 " + targetPackage + " 领域的 " + target.getName()));
                }
            }
        };
    }

    private static String businessPackageOf(String className) {
        String prefix = "com.xiaozhi.";
        if (!className.startsWith(prefix)) {
            return "";
        }
        String rest = className.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** 按方法全名（含参数类型）精确匹配白名单，重载之间不会互相误放行 */
    private static DescribedPredicate<JavaMethod> hasFullNameIn(Set<String> fullNames) {
        return DescribedPredicate.describe("全名在白名单里", method -> fullNames.contains(method.getFullName()));
    }

    private static DescribedPredicate<JavaClass> hasNameIn(Set<String> names) {
        return new DescribedPredicate<>("已登记的存量违规") {
            @Override
            public boolean test(JavaClass javaClass) {
                return names.contains(javaClass.getName());
            }
        };
    }
}
