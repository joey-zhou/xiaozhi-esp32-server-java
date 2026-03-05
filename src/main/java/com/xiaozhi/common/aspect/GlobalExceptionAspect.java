package com.xiaozhi.common.aspect;

import com.xiaozhi.common.context.RequestContextHolder;
import com.xiaozhi.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 全局方法调用异常拦截切面
 * 拦截所有 Service、Controller、Repository 方法，记录异常信息
 *
 * @author Joey
 */
@Aspect
@Component
public class GlobalExceptionAspect {

    private static final Logger log = LogUtils.getLogger(GlobalExceptionAspect.class);

    /**
     * 定义切点：Service 和 Controller 层方法（排除 common、security、config、aspect 包）
     */
    @Pointcut("(" +
            "within(@org.springframework.stereotype.Service *) || " +
              "within(@org.springframework.web.bind.annotation.RestController *)) && " +
              "!within(com.xiaozhi.common..*) && " +
              "!within(com.xiaozhi.security..*) && " +
//              "!within(com.xiaozhi.config..*) && " +
              "!within(com.xiaozhi.common.aspect..*) && " +
              "!within(com.xiaozhi.common.interceptor..*) && " +
              "!within(com.xiaozhi.common.context..*) && " +
              "!within(com.xiaozhi.common.util..*) && " +
              "!within(com.xiaozhi.common.exception..*)")
    public void businessMethods() {
    }

    /**
     * 环绕通知：拦截方法调用并处理异常
     */
    @Around("businessMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 执行目标方法
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 记录异常信息
            handleException(joinPoint, e);
            // 抛出异常，让上层处理
            throw e;
        }
    }

    /**
     * 处理异常并记录日志
     */
    private void handleException(ProceedingJoinPoint joinPoint, Throwable e) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = method.getName();

        // 构建方法调用信息
        String methodCall = buildMethodCallInfo(className, methodName, joinPoint.getArgs());

        // 获取请求信息
        String requestInfo = RequestContextHolder.getFullRequestInfo();
        String apiPath = RequestContextHolder.getApiPath();

        // 记录异常日志
        log.error("\n========== 方法调用异常 ==========\n" +
                  "【请求信息】: {}\n" +
                  "【API 路径】: {}\n" +
                  "【调用类】: {}\n" +
                  "【调用方法】: {}\n" +
                  "【异常类型】: {}\n" +
                  "【异常信息】: {}\n" +
                  "【调用栈】:\n{}\n" +
                  "==================================",
                requestInfo,
                apiPath,
                className,
                methodCall,
                e.getClass().getSimpleName(),
                e.getMessage(),
                buildStackTrace(e),
                e);
    }

    /**
     * 构建方法调用信息（包含参数）
     */
    private String buildMethodCallInfo(String className, String methodName, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(".").append(methodName).append("(");

        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                // 参数类型
                String argType = args[i] != null ? args[i].getClass().getSimpleName() : "null";
                // 参数值（简单类型才显示）
                String argValue = formatArgValue(args[i]);
                sb.append(argType).append("=").append(argValue);
            }
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * 格式化参数值
     */
    private String formatArgValue(Object arg) {
        if (arg == null) {
            return "null";
        }

        // 基本类型和简单对象直接显示
        if (arg instanceof String ||
            arg instanceof Number ||
            arg instanceof Boolean ||
            arg instanceof Character) {
            String str = arg.toString();
            // 字符串超过 100 字符截断
            if (str.length() > 100) {
                return str.substring(0, 100) + "...";
            }
            return str;
        }

        // 数组类型
        if (arg.getClass().isArray()) {
            return "[Array]";
        }

        // 集合类型
        if (arg instanceof java.util.Collection) {
            return "[Collection size=" + ((java.util.Collection<?>) arg).size() + "]";
        }

        // Map 类型
        if (arg instanceof java.util.Map) {
            return "[Map size=" + ((java.util.Map<?, ?>) arg).size() + "]";
        }

        // 其他对象只显示类名
        return "[" + arg.getClass().getSimpleName() + "]";
    }

    /**
     * 构建异常调用栈（只显示业务代码）
     */
    private String buildStackTrace(Throwable e) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTraces = e.getStackTrace();

        for (StackTraceElement element : stackTraces) {
            String className = element.getClassName();
            // 只显示业务代码
            if (isBusinessCode(className)) {
                sb.append("    at ").append(element.toString()).append("\n");
            }
        }

        // 如果没有业务代码，显示全部
        if (sb.length() == 0) {
            for (int i = 0; i < Math.min(10, stackTraces.length); i++) {
                sb.append("    at ").append(stackTraces[i].toString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 判断是否是业务代码
     */
    private boolean isBusinessCode(String className) {
        if (!className.startsWith("com.xiaozhi.")) {
            return false;
        }
        // 排除工具类、配置类等
        if (className.startsWith("com.xiaozhi.common.") ||
            className.startsWith("com.xiaozhi.security.") ||
            className.startsWith("com.xiaozhi.config.") ||
            className.startsWith("com.xiaozhi.entity.") ||
            className.startsWith("com.xiaozhi.dto.") ||
            className.endsWith("Util") ||
            className.endsWith("Config") ||
            className.endsWith("Aspect") ||
            className.endsWith("Interceptor")) {
            return false;
        }
        return true;
    }
}
