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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Component
public class OwnershipAspect {

    private final Map<String, OwnershipChecker> checkerMap = new LinkedHashMap<>();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

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
