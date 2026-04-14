package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guard tests that enforce DDD Phase 1 conventions:
 * Service interfaces and implementations must not depend on *Req DTOs.
 * <p>
 * Controllers are the only layer allowed to reference Req objects; they must
 * unwrap / convert them before calling Service methods.
 */
class ServiceLayerArchTest {

    private static JavaClasses serviceClasses;

    @BeforeAll
    static void importClasses() {
        serviceClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.xiaozhi.agent.service",
                "com.xiaozhi.authrole.service",
                "com.xiaozhi.config.service",
                "com.xiaozhi.device.service",
                "com.xiaozhi.message.service",
                "com.xiaozhi.role.service",
                "com.xiaozhi.template.service",
                "com.xiaozhi.user.service",
                "com.xiaozhi.summary.service"
            );
    }

    @Test
    void serviceLayerShouldNotDependOnCommonReqPackage() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..service..", "..service.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.xiaozhi.common.model.req..")
            .because("Service layer must not depend on common *Req DTOs — " +
                "controllers should unwrap Req objects into individual params or BOs");

        rule.check(serviceClasses);
    }

    @Test
    void serviceLayerShouldNotDependOnDomainLocalReqPackage() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..service..", "..service.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("..model.req..")
            .because("Service layer must not depend on domain-local *Req DTOs — " +
                "controllers should unwrap Req objects into individual params or BOs");

        rule.check(serviceClasses);
    }
}
