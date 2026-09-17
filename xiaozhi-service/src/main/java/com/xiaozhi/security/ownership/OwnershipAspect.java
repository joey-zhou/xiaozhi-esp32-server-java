package com.xiaozhi.security.ownership;

import cn.dev33.satoken.stp.StpUtil;
import com.xiaozhi.common.annotation.CheckOwner;
import com.xiaozhi.common.exception.UnauthorizedException;
import com.xiaozhi.common.model.bo.UserBO;
import com.xiaozhi.user.service.UserService;
import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
public class OwnershipAspect {

    /** SpEL 里对方法参数的引用，如 #roleId、#param.roleId 取到的根变量名。 */
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("#(\\w+)");

    /** MethodBasedEvaluationContext 除参数名外还注入的内建变量。 */
    private static final Set<String> BUILTIN_VARIABLES = Set.of("root", "this");

    private final Map<String, OwnershipChecker> checkerMap = new LinkedHashMap<>();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    /** 已通过表达式变量名校验的方法，校验只做一次。 */
    private final Map<Method, Boolean> validatedMethods = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    public OwnershipAspect(List<OwnershipChecker> checkers) {
        for (OwnershipChecker checker : checkers) {
            checkerMap.put(checker.getResource(), checker);
        }
    }

    @Before("@annotation(com.xiaozhi.common.annotation.CheckOwner) || @annotation(com.xiaozhi.common.annotation.CheckOwners)")
    public void checkOwner(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CheckOwner[] annotations = method.getAnnotationsByType(CheckOwner.class);
        if (annotations.length == 0) {
            return;
        }

        validatedMethods.computeIfAbsent(method, m -> requireResolvableVariables(m, annotations));

        StpUtil.checkLogin();
        Integer userId = resolveCurrentUserId();

        MethodBasedEvaluationContext context =
            new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(), parameterNameDiscoverer);

        for (CheckOwner annotation : annotations) {
            if (annotation.adminBypass() && isAdmin(userId)) {
                continue;
            }

            OwnershipChecker checker = checkerMap.get(annotation.resource());
            if (checker == null) {
                throw new IllegalStateException("未注册资源归属检查器: " + annotation.resource());
            }

            Object resourceId = expressionParser.parseExpression(annotation.id()).getValue(context);
            for (Object candidateId : resolveIds(resourceId)) {
                if (candidateId == null) {
                    continue;
                }
                if (candidateId instanceof String text && text.isBlank()) {
                    continue;
                }
                checker.check(candidateId, userId);
            }
        }
    }

    /**
     * 表达式里引用的变量必须都是该方法的真实参数名，否则 SpEL 求值为 null，整条归属校验会被静默跳过。
     * 参数名取不到（编译未带 -parameters）时同样判失败，此时所有 #param 都会解析为 null。
     */
    private boolean requireResolvableVariables(Method method, CheckOwner[] annotations) {
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames == null) {
            throw new IllegalStateException("无法解析方法参数名，归属校验表达式不可用: " + method);
        }
        Set<String> knownVariables = new HashSet<>(Arrays.asList(parameterNames));
        knownVariables.addAll(BUILTIN_VARIABLES);
        for (int i = 0; i < parameterNames.length; i++) {
            knownVariables.add("a" + i);
            knownVariables.add("p" + i);
        }

        for (CheckOwner annotation : annotations) {
            Matcher matcher = VARIABLE_REFERENCE.matcher(annotation.id());
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (!knownVariables.contains(variable)) {
                    throw new IllegalStateException(
                        "归属校验表达式引用了不存在的方法参数 #" + variable + ": " + method);
                }
            }
        }
        return true;
    }

    private Integer resolveCurrentUserId() {
        Object loginId;
        try {
            loginId = StpUtil.getLoginId();
        } catch (Exception e) {
            throw new UnauthorizedException("无法获取当前登录用户");
        }
        if (loginId == null) {
            throw new UnauthorizedException("无法获取当前登录用户");
        }
        try {
            if (loginId instanceof Number number) {
                return Math.toIntExact(number.longValue());
            }
            String text = loginId.toString().trim();
            if (text.isEmpty()) {
                throw new NumberFormatException("blank login id");
            }
            return Integer.valueOf(text);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new UnauthorizedException("无法获取当前登录用户");
        }
    }

    private boolean isAdmin(Integer userId) {
        UserBO user = userService.getBO(userId);
        return user != null && "1".equals(user.getIsAdmin());
    }

    private List<Object> resolveIds(Object resourceId) {
        if (resourceId == null) {
            return List.of();
        }
        if (resourceId instanceof Iterable<?> iterable) {
            List<Object> resourceIds = new ArrayList<>();
            iterable.forEach(resourceIds::add);
            return resourceIds;
        }
        if (resourceId.getClass().isArray()) {
            int length = Array.getLength(resourceId);
            List<Object> resourceIds = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                resourceIds.add(Array.get(resourceId, i));
            }
            return resourceIds;
        }
        return List.of(resourceId);
    }
}
