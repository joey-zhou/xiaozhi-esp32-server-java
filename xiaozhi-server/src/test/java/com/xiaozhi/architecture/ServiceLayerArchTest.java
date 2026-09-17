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
 * Controller 的 public 方法出参只允许 Resp、PageResult、ApiResponse、原语与 java/jakarta/spring/reactor 类型，
 * 其中 java.util.Map 系列不算放行类型（裸 Map 出参没有字段契约），确需返回无固定 schema 的外部协议负载时
 * 逐个加进 {@link #DYNAMIC_PAYLOAD_ALLOWLIST} 并写明原因；
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

    private static final String API_RESPONSE = "com.xiaozhi.common.web.ApiResponse";
    private static final String PAGE_RESULT = "com.xiaozhi.common.model.PageResult";

    /** 出参本身就是无固定 schema 的外部协议负载，没有 DTO 可收口，逐个显式豁免而非放宽通用规则。 */
    private static final Set<String> DYNAMIC_PAYLOAD_ALLOWLIST = Set.of(
        // MCP 工具调用结果的结构由被调用的第三方工具自行定义，无法用固定 DTO 表达
        "com.xiaozhi.device.DeviceMcpController#callMcpTool",
        // 设备端 MCP 识图回调，响应结构随视觉模型返回内容变化，且是设备固件直接解析的协议负载
        "com.xiaozhi.communication.controller.VLChatController#vlChat"
    );

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
            .should().dependOnClassesThat()
            .resideInAPackage("..model.resp..")
            .because("Resp 的组装只发生在 server 模块，读侧 Service 返回 BO 或包内只读投影");

        rule.check(xiaozhiClasses);
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

    private static Stream<JavaClass> controllers() {
        return xiaozhiClasses.stream().filter(c -> c.getSimpleName().endsWith("Controller"));
    }

    private static ArchCondition<JavaClass> onlyReturnWebTypesFromPublicMethods() {
        return new ArchCondition<>("public 方法的返回类型只含 Resp、PageResult、ApiResponse、原语与 java/jakarta/spring/reactor 类型") {
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
        if (DYNAMIC_PAYLOAD_ALLOWLIST.contains(method.getOwner().getName() + "#" + method.getName())) {
            return Set.of();
        }
        return method.getReturnType().getAllInvolvedRawTypes().stream()
            .filter(type -> !isWebType(type))
            .map(JavaClass::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 出参一旦含 Map，字段契约就退化成裸 key/value，前后端只能靠人工对齐——不属于放行的 java.* 类型。 */
    private static final Set<String> UNTYPED_JAVA_TYPES = Set.of(
        "java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap",
        "java.util.TreeMap", "java.util.SortedMap", "java.util.NavigableMap"
    );

    private static boolean isWebType(JavaClass type) {
        String name = type.getName();
        String pkg = type.getPackageName() + ".";
        return type.isPrimitive()
            || "void".equals(name)
            || (name.startsWith("java.") && !UNTYPED_JAVA_TYPES.contains(name))
            || name.startsWith("jakarta.")
            || name.startsWith("org.springframework.")
            || name.startsWith("reactor.")
            || API_RESPONSE.equals(name)
            || PAGE_RESULT.equals(name)
            || pkg.contains(".model.resp.");
    }
}
