package com.xiaozhi.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.annotation.Sensitive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * 词表只是下限，名字不像凭证的字段仍须手工标 {@link Sensitive}。
 */
class SensitiveFieldArchTest {

    private static final Set<String> CREDENTIAL_NAMES = Set.of("ak", "sk", "token", "credential");

    private static final List<String> CREDENTIAL_PARTS =
        List.of("password", "secret", "apikey", "privatekey", "accesskey");

    private static final DescribedPredicate<JavaField> NAMED_LIKE_CREDENTIAL =
        new DescribedPredicate<>("名字表明装的是凭证") {
            @Override
            public boolean test(JavaField field) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                return CREDENTIAL_NAMES.contains(name) || CREDENTIAL_PARTS.stream().anyMatch(name::contains);
            }
        };

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void credentialFieldsInRequestDtoAreMarkedSensitive() {
        ArchRule rule = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..common.model.req..")
            .and(NAMED_LIKE_CREDENTIAL)
            .should().beAnnotatedWith(Sensitive.class)
            .because("审计切面按 @Sensitive 打码，漏标的字段会以原值写进 sys_operation_log");

        rule.check(xiaozhiClasses);
    }

    @Test
    void auditLogIsOnlyAppliedToControllers() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(AuditLog.class)
            .should().beDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .because("审计只在 Controller 记入参，@AuditLog 下沉到 Service 会把 BO/DO 也变成泄漏面，"
                + "而 credentialFieldsInRequestDtoAreMarkedSensitive 只管得住 Req");

        rule.check(xiaozhiClasses);
    }
}
