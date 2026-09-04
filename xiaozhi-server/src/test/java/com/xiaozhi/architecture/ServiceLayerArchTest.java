package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分层约束：Service 层不得依赖 Req/Resp DTO。
 * <p>
 * Req 只允许出现在 Controller，进入 Service 之前必须先拆成独立入参或转成 BO；
 * Resp 的组装只发生在 server 模块，读侧 Service 返回 BO 或包内只读投影；
 * Controller 的 public 方法出参只允许 Resp、PageResult、ApiResponse、原语与 java/jakarta/spring 类型；
 * 投影类只放 {pkg}/model 且只许本顶层业务包引用。
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
        "com\\.xiaozhi\\.()\\..*";

    /** 包名必须与 {@link #RESP_KNOWN_VIOLATIONS} 逐字对应。 */
    private static final String[] RESP_VIOLATION_PACKAGES = {
    };

    private static final String CONTROLLER_RETURN_KNOWN_VIOLATIONS =
        "com\\.xiaozhi\\.server\\.web\\.chat\\.WebChatController";

    private static final String API_RESPONSE = "com.xiaozhi.common.web.ApiResponse";
    private static final String PAGE_RESULT = "com.xiaozhi.common.model.PageResult";

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
            .map(ServiceLayerArchTest::businessPackage)
            .collect(Collectors.toSet());

        assertThat(actual)
            .as("读侧返回 Resp 的包变了，请同步改 RESP_KNOWN_VIOLATIONS 与 RESP_VIOLATION_PACKAGES")
            .containsExactlyInAnyOrder(RESP_VIOLATION_PACKAGES);
    }

    private static boolean isBelowServer(JavaClass javaClass) {
        String pkg = javaClass.getPackageName() + ".";
        return pkg.contains(".service.") || pkg.contains(".dal.") || pkg.contains(".security.");
    }

    /** com.xiaozhi 之后的第一段包名。 */
    private static String businessPackage(JavaClass javaClass) {
        return javaClass.getPackageName().replaceFirst("^com\\.xiaozhi\\.", "").split("\\.")[0];
    }

    @Test
    void projectionsStayInsideTheirBusinessPackage() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Projection")
            .should().resideInAPackage("..model..")
            .andShould().resideOutsideOfPackage("..dal..")
            .andShould(onlyBeDependedOnByTheirOwnBusinessPackage())
            .because("投影是 SQL 直出的包内只读结果集，只放 {pkg}/model 且只许本顶层业务包引用，ai/dialogue 需要它就说明它是 BO");

        rule.check(xiaozhiClasses);
    }

    private static ArchCondition<JavaClass> onlyBeDependedOnByTheirOwnBusinessPackage() {
        return new ArchCondition<>("只被同一顶层业务包 com.xiaozhi.<x>.. 引用") {
            @Override
            public void check(JavaClass projection, ConditionEvents events) {
                String owner = businessPackage(projection);
                for (Dependency dependency : projection.getDirectDependenciesToSelf()) {
                    if (!owner.equals(businessPackage(dependency.getOriginClass()))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    @Test
    void controllerReturnTypesAreRespOnly() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().haveNameNotMatching(CONTROLLER_RETURN_KNOWN_VIOLATIONS)
            .should(onlyReturnWebTypesFromPublicMethods())
            .because("Controller 出参只能是 Resp、PageResult<Resp>、原语或 Void，BO/DO/投影直接出 web 就绕开了 Resp 这道字段收口");

        rule.check(xiaozhiClasses);
    }

    @Test
    void controllersAreActuallyScanned() {
        assertThat(controllers().count())
            .as("扫到的 Controller 太少，出参规则会假绿")
            .isGreaterThanOrEqualTo(10);
    }

    @Test
    void controllerReturnViolationsMatchTheRegistryExactly() {
        Set<String> actual = controllers()
            .filter(c -> c.getMethods().stream().anyMatch(m -> !nonWebReturnTypes(m).isEmpty()))
            .map(JavaClass::getName)
            .collect(Collectors.toSet());
        Set<String> registered = controllers()
            .filter(c -> c.getName().matches(CONTROLLER_RETURN_KNOWN_VIOLATIONS))
            .map(JavaClass::getName)
            .collect(Collectors.toSet());

        assertThat(actual)
            .as("出参越界的 Controller 变了，请同步改 CONTROLLER_RETURN_KNOWN_VIOLATIONS")
            .containsExactlyInAnyOrderElementsOf(registered);
    }

    private static Stream<JavaClass> controllers() {
        return xiaozhiClasses.stream().filter(c -> c.getSimpleName().endsWith("Controller"));
    }

    private static ArchCondition<JavaClass> onlyReturnWebTypesFromPublicMethods() {
        return new ArchCondition<>("public 方法的返回类型只含 Resp、PageResult、ApiResponse、原语与 java/jakarta/spring 类型") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    Set<String> offending = nonWebReturnTypes(method);
                    if (!offending.isEmpty()) {
                        events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 返回类型含 " + offending));
                    }
                }
            }
        };
    }

    /** 返回 public 方法返回类型（含泛型内层）里不属于 web 出参的原始类型全名，非 public 方法返回空集。 */
    private static Set<String> nonWebReturnTypes(JavaMethod method) {
        if (!method.getModifiers().contains(JavaModifier.PUBLIC)
            || method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
            return Set.of();
        }
        return method.getReturnType().getAllInvolvedRawTypes().stream()
            .filter(type -> !isWebType(type))
            .map(JavaClass::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean isWebType(JavaClass type) {
        String name = type.getName();
        String pkg = type.getPackageName() + ".";
        return type.isPrimitive()
            || "void".equals(name)
            || name.startsWith("java.")
            || name.startsWith("jakarta.")
            || name.startsWith("org.springframework.")
            || API_RESPONSE.equals(name)
            || PAGE_RESULT.equals(name)
            || pkg.contains(".model.resp.");
    }
}
