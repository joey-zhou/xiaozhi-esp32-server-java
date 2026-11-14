package com.xiaozhi.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.annotation.CheckOwners;
import com.xiaozhi.security.ownership.OwnershipChecker;
import com.xiaozhi.security.ownership.OwnershipConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code @CheckOwner} 的契约在构建期就要成立，别等请求打进来才发现：
 * <ul>
 *   <li>id 表达式里的 {@code #变量} 必须是该方法真实的参数名，写错会被 SpEL 求值成 null，整条归属校验静默跳过；</li>
 *   <li>resource 必须有对应的已注册 checker，写错会在运行时抛「未注册资源归属检查器」。</li>
 * </ul>
 * 变量名规则与 OwnershipAspect 的运行时 fail-closed 校验一致，这里只是把同一条规则提前到构建期。
 */
class CheckOwnerContractArchTest {

    /** 与 OwnershipAspect 同一条：SpEL 里对方法参数的引用 */
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("#(\\w+)");

    /** MethodBasedEvaluationContext 除参数名外还注入的内建变量 */
    private static final Set<String> BUILTIN_VARIABLES = Set.of("root", "this");

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /** 参数名以 Id 或 Ids 结尾的算资源 id */
    private static final Pattern RESOURCE_ID = Pattern.compile(".*Ids?");

    /** 归属不走 @CheckOwner 的端点 → 由谁判定、或为什么不需要 */
    private static final Map<String, String> OWNERSHIP_SELF_CHECKED = Map.ofEntries(
        Map.entry("AuthRoleController#assignPermissions", "后台权限角色是全局资源，只按权限键"),
        Map.entry("AuthRoleController#getPermissionConfig", "后台权限角色是全局资源，只按权限键"),
        Map.entry("ConversationController#rename", "ConversationService.checkAccess"),
        Map.entry("WebChatController#close", "WebChatAppService.closeSession（进程内会话按开它的用户判）"));

    private static JavaClasses xiaozhiClasses;

    @BeforeAll
    static void importClasses() {
        xiaozhiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiaozhi");
    }

    @Test
    void annotatedMethodsExist() {
        assertThat(annotatedMethods())
            .as("扫不到任何 @CheckOwner 说明扫描范围错了，后面两条断言会假通过")
            .isNotEmpty();
    }

    @Test
    void everyIdExpressionOnlyReferencesRealParameterNames() {
        List<String> violations = new ArrayList<>();
        for (Method method : annotatedMethods()) {
            String[] parameterNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
            if (parameterNames == null) {
                violations.add(method + " 取不到参数名，编译需带 -parameters");
                continue;
            }
            Set<String> known = new HashSet<>(Arrays.asList(parameterNames));
            known.addAll(BUILTIN_VARIABLES);
            for (int i = 0; i < parameterNames.length; i++) {
                known.add("a" + i);
                known.add("p" + i);
            }
            for (CheckOwner annotation : method.getAnnotationsByType(CheckOwner.class)) {
                Matcher matcher = VARIABLE_REFERENCE.matcher(annotation.id());
                while (matcher.find()) {
                    if (!known.contains(matcher.group(1))) {
                        violations.add(method + " 的 id 表达式引用了不存在的参数 #" + matcher.group(1)
                            + "，实际参数名 " + Arrays.toString(parameterNames));
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void everyResourceNameHasRegisteredChecker() {
        Set<String> registered = registeredResources();
        Set<String> used = new TreeSet<>();
        for (Method method : annotatedMethods()) {
            for (CheckOwner annotation : method.getAnnotationsByType(CheckOwner.class)) {
                used.add(annotation.resource());
            }
        }

        assertThat(used).isNotEmpty();
        assertThat(used).isSubsetOf(registered);
    }

    /**
     * Controller 以路径变量或查询参数接收资源 id 的方法，要么带 @CheckOwner，
     * 要么登记在 {@link #OWNERSHIP_SELF_CHECKED}，写明由哪个 Service 方法判定。集合相等：判定挪走了名单也要跟着删。
     */
    @Test
    void endpointsTakingAResourceIdCheckOwnership() {
        Set<String> unchecked = new TreeSet<>();
        for (Method method : endpointMethods()) {
            if (method.isAnnotationPresent(CheckOwner.class) || method.isAnnotationPresent(CheckOwners.class)) {
                continue;
            }
            if (takesResourceId(method)) {
                unchecked.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName());
            }
        }

        assertThat(unchecked)
            .as("带资源 id 的端点加 @CheckOwner；归属由 Service 自己判定的登记进 OWNERSHIP_SELF_CHECKED 并写明方法")
            .isEqualTo(OWNERSHIP_SELF_CHECKED.keySet());
    }

    private static boolean takesResourceId(Method method) {
        String[] names = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (!parameter.isAnnotationPresent(PathVariable.class) && !parameter.isAnnotationPresent(RequestParam.class)) {
                continue;
            }
            String name = names == null ? parameter.getName() : names[i];
            if (RESOURCE_ID.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Method> endpointMethods() {
        List<Method> methods = new ArrayList<>();
        for (JavaClass controller : xiaozhiClasses) {
            if (!controller.isAnnotatedWith(RestController.class) && !controller.isAnnotatedWith(Controller.class)) {
                continue;
            }
            for (JavaMethod javaMethod : controller.getMethods()) {
                if (javaMethod.isAnnotatedWith(RequestMapping.class) || javaMethod.getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getRawType().isAnnotatedWith(RequestMapping.class))) {
                    methods.add(javaMethod.reflect());
                }
            }
        }
        return methods;
    }

    /** 全仓所有带 @CheckOwner / @CheckOwners 的方法 */
    private static List<Method> annotatedMethods() {
        List<Method> methods = new ArrayList<>();
        for (JavaMethod javaMethod : xiaozhiClasses.stream()
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(javaMethod -> javaMethod.isAnnotatedWith(CheckOwner.class)
                || javaMethod.isAnnotatedWith(CheckOwners.class))
            .toList()) {
            methods.add(javaMethod.reflect());
        }
        return methods;
    }

    /** 反射调用配置类里所有产出 OwnershipChecker 的工厂方法，取它们声明的资源名 */
    private static Set<String> registeredResources() {
        Set<String> resources = new LinkedHashSet<>();
        for (Object config : List.of(new OwnershipConfig())) {
            for (Method method : config.getClass().getDeclaredMethods()) {
                if (!OwnershipChecker.class.isAssignableFrom(method.getReturnType())
                    || !Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                Object[] args = Arrays.stream(method.getParameterTypes()).map(t -> (Object) mock(t)).toArray();
                try {
                    resources.add(((OwnershipChecker) method.invoke(config, args)).getResource());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("无法实例化归属检查器: " + method, e);
                }
            }
        }
        return resources;
    }
}
