package com.xiaozhi.common.aspect;

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
 * 异步方法异常拦截切面
 * 拦截所有标注 @Async 的异步方法
 *
 * @author Joey
 */
@Aspect
@Component
public class AsyncExceptionAspect {

    private static final Logger log = LogUtils.getLogger(AsyncExceptionAspect.class);

    /**
     * 定义切点：所有标注 @Async 的方法
     */
    @Pointcut("@annotation(org.springframework.scheduling.annotation.Async)")
    public void asyncMethods() {
    }

    /**
     * 环绕通知：拦截异步方法调用
     */
    @Around("asyncMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = getFullMethodName(joinPoint);
        
        try {
            log.debug("【异步任务开始】{}", methodName);
            Object result = joinPoint.proceed();
            log.debug("【异步任务完成】{}", methodName);
            return result;
        } catch (Throwable e) {
            // 记录异常信息
            handleException(joinPoint, e);
            // 异步任务异常需要重新抛出
            throw e;
        }
    }

    /**
     * 处理异常并记录日志
     */
    private void handleException(ProceedingJoinPoint joinPoint, Throwable e) {
        String methodName = getFullMethodName(joinPoint);
        String paramInfo = getParamInfo(joinPoint);

        log.error("\n========== 异步任务异常 ==========\n" +
                  "【任务方法】: {}\n" +
                  "【方法参数】: {}\n" +
                  "【异常类型】: {}\n" +
                  "【异常信息】: {}\n" +
                  "【调用栈】:\n{}",
                methodName,
                paramInfo,
                e.getClass().getSimpleName(),
                e.getMessage(),
                buildStackTrace(e),
                e);
    }

    /**
     * 获取完整方法名
     */
    private String getFullMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * 获取参数信息
     */
    private String getParamInfo(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "无参数";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatArgValue(args[i]));
        }
        return sb.toString();
    }

    /**
     * 格式化参数值
     */
    private String formatArgValue(Object arg) {
        if (arg == null) {
            return "null";
        }

        if (arg instanceof String ||
            arg instanceof Number ||
            arg instanceof Boolean) {
            String str = arg.toString();
            if (str.length() > 200) {
                return str.substring(0, 200) + "...";
            }
            return str;
        }

        if (arg.getClass().isArray()) {
            return "[Array]";
        }

        if (arg instanceof java.util.Collection) {
            return "[Collection size=" + ((java.util.Collection<?>) arg).size() + "]";
        }

        return "[" + arg.getClass().getSimpleName() + "]";
    }

    /**
     * 构建异常调用栈
     */
    private String buildStackTrace(Throwable e) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTraces = e.getStackTrace();

        for (int i = 0; i < Math.min(15, stackTraces.length); i++) {
            sb.append("    at ").append(stackTraces[i].toString()).append("\n");
        }

        return sb.toString();
    }
}
