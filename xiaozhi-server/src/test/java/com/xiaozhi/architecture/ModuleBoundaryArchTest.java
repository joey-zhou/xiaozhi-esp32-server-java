package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界约束：反向需求一律经 common/port 接口倒置。
 * <p>
 * 全模块规则靠 xiaozhi-server 的 test 作用域 xiaozhi-dialogue 依赖才扫得到 dialogue 类；
 * 去掉那条依赖或把本类挪出 server 模块，规则照常通过，只是不再覆盖 dialogue。
 */
class ModuleBoundaryArchTest {

    /** communication.auth 的源码在 xiaozhi-common，不在这两个包里 */
    private static final String[] DIALOGUE_PACKAGES = {"com.xiaozhi.dialogue..", "com.xiaozhi.communication.."};

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void dialogueDoesNotReachIntoDomainRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(DIALOGUE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.repository..")
            .because("实时链路写设备走 common/port/DeviceWriter，直连 Repository 会连聚合根一起绕开");

        rule.check(xiaozhiClasses);
    }

    @Test
    void dialogueDoesNotReachIntoServiceImplementations() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(DIALOGUE_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..service.impl..")
            .because("下游模块只依赖 Service 接口或 common/port，依赖实现类会把事务与缓存细节泄漏到会话链路");

        rule.check(xiaozhiClasses);
    }
}
