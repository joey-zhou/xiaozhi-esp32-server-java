package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分层约束：Service 层不得依赖 Req/Resp DTO。
 * <p>
 * Req 只允许出现在 Controller，进入 Service 之前必须先拆成独立入参或转成 BO；
 * Resp 的组装只发生在 server 模块，读侧 Service 返回 BO 或包内只读投影。
 * <p>
 * 扫描范围是整个 com.xiaozhi，新增业务模块自动纳管，不维护包白名单。
 * 依赖只在 xiaozhi-server 声明的 archunit，且要一次扫全部业务模块的编译产物，所以留在 server 模块。
 */
class ServiceLayerArchTest {

    /** 不含 {@code ..convert..}：转换器的职责就是 Req/Resp ↔ BO/DO。 */
    private static final String[] BELOW_SERVER_PACKAGES = {
        "..service..", "..dal..", "..security.."
    };

    private static final String RESP_KNOWN_VIOLATIONS =
        "com\\.xiaozhi\\.(agent|authrole|config|device|message"
            + "|permission|role|security|template|user)\\..*";

    /** 包名必须与 {@link #RESP_KNOWN_VIOLATIONS} 逐字对应。 */
    private static final String[] RESP_VIOLATION_PACKAGES = {
        "agent", "authrole", "config", "device", "message",
        "permission", "role", "security", "template", "user"
    };

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
            .that().resideInAnyPackage(BELOW_SERVER_PACKAGES)
            .should().dependOnClassesThat()
            .resideInAPackage("..model.req..")
            .because("Service 层不得依赖 *Req DTO，Controller 应先把 Req 拆成独立入参或 BO");

        rule.check(xiaozhiClasses);
    }

    @Test
    void serviceLayerDoesNotDependOnRespDtoPackage() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(BELOW_SERVER_PACKAGES)
            .and().haveNameNotMatching(RESP_KNOWN_VIOLATIONS)
            .should().dependOnClassesThat()
            .resideInAPackage("..model.resp..")
            .because("Resp 的组装只发生在 server 模块，读侧 Service 返回 BO 或包内只读投影");

        rule.check(xiaozhiClasses);
    }

    /**
     * 豁免名单一旦写宽，新包的违规会被悄悄吸收；清零一个包却忘了删名单，清单就不会变短。
     * 比集合而不是比数量，两个方向才都会红。
     */
    @Test
    void respViolationPackagesMatchTheRegistryExactly() {
        Set<String> actual = xiaozhiClasses.stream()
            .filter(ServiceLayerArchTest::isBelowServer)
            .filter(c -> c.getDirectDependenciesFromSelf().stream()
                .anyMatch(d -> d.getTargetClass().getPackageName().contains(".model.resp")))
            .map(c -> c.getPackageName().replaceFirst("^com\\.xiaozhi\\.", "").split("\\.")[0])
            .collect(Collectors.toSet());

        assertThat(actual)
            .as("读侧返回 Resp 的包变了，请同步改 RESP_KNOWN_VIOLATIONS 与 RESP_VIOLATION_PACKAGES")
            .containsExactlyInAnyOrder(RESP_VIOLATION_PACKAGES);
    }

    private static boolean isBelowServer(JavaClass javaClass) {
        String pkg = javaClass.getPackageName() + ".";
        return pkg.contains(".service.") || pkg.contains(".dal.") || pkg.contains(".security.");
    }
}
