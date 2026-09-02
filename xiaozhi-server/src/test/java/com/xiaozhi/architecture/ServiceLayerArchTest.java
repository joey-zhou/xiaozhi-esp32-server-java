package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层约束：Service 接口与实现不得依赖 *Req DTO，*Req 只允许出现在 Controller，
 * 进入 Service 之前必须先拆成独立入参或转成 BO。
 * <p>
 * 扫描范围是整个 com.xiaozhi，新增业务模块自动纳管，不再维护包白名单（旧白名单漏掉过 11 个 service 包）。
 * 依赖只在 xiaozhi-server 声明的 archunit，且要一次扫全部业务模块的编译产物，所以留在 server 模块。
 */
class ServiceLayerArchTest {

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void serviceLayerDoesNotDependOnReqDtoPackage() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat()
            .resideInAPackage("..model.req..")
            .because("Service 层不得依赖 *Req DTO，Controller 应先把 Req 拆成独立入参或 BO");

        rule.check(xiaozhiClasses);
    }
}
