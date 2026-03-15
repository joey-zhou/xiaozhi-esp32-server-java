package com.xiaozhi.common.aspect;

import com.xiaozhi.utils.AuthUtils;
import com.xiaozhi.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局方法调用跟踪切面
 * 拦截所有方法调用，记录耗时、异常和调用次数
 *
 * @author Joey
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MethodTraceAspect {

    private static final Logger log = LogUtils.getLogger(MethodTraceAspect.class);

    /**
     * 方法调用统计
     */
    private static final ConcurrentHashMap<String, AtomicLong> METHOD_CALL_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> METHOD_ERROR_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> METHOD_TOTAL_TIME = new ConcurrentHashMap<>();

    /**
     * 是否启用方法跟踪
     */
    @Value("${aop.trace-enabled:true}")
    private boolean traceEnabled;

    /**
     * 慢调用阈值（毫秒）
     */
    @Value("${aop.slow-call-threshold:1000}")
    private long slowCallThreshold;

    /**
     * 定义切点：仅跟踪 Repository 层方法
     */
    @Pointcut("execution(* com.xiaozhi.repository..*(..))")
    public void allMethods() {
    }

    /**
     * 环绕通知：拦截所有方法调用
     */
    @Around("allMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 如果未启用跟踪，直接执行方法
        if (!traceEnabled) {
            return joinPoint.proceed();
        }

        String methodName = getFullMethodName(joinPoint);
        Instant startTime = Instant.now();

        // 增加调用计数
        incrementCallCount(methodName);

        try {
            // 记录方法调用开始（DEBUG 级别）
            log.debug("【方法调用】>> {}", methodName);

            // 执行目标方法
            Object result = joinPoint.proceed();

            // 计算耗时
            Duration duration = Duration.between(startTime, Instant.now());
            long millis = duration.toMillis();

            // 更新统计
            updateStatistics(methodName, millis, true);

            // 记录方法调用完成
            if (millis > slowCallThreshold) {
                log.warn("【慢调用】{} [耗时：{}ms] ⚠️", methodName, millis);
            } else {
                log.debug("【方法完成】<< {} [耗时：{}ms]", methodName, millis);
            }

            return result;
        } catch (Throwable e) {
            // 计算耗时
            Duration duration = Duration.between(startTime, Instant.now());
            long millis = duration.toMillis();

            // 更新统计（失败）
            updateStatistics(methodName, millis, false);

            // 记录异常
            handleException(joinPoint, e, duration);

            // 抛出异常
            throw e;
        }
    }

    /**
     * 处理异常并记录日志
     */
    private void handleException(ProceedingJoinPoint joinPoint, Throwable e, Duration duration) {
        String methodName = getFullMethodName(joinPoint);
        String paramInfo = getParamInfo(joinPoint);
        String requestInfo = AuthUtils.getFullRequestInfo();

        log.error("\n========== 方法调用异常 ==========\n" +
                  "【请求信息】: {}\n" +
                  "【调用方法】: {}\n" +
                  "【方法参数】: {}\n" +
                  "【执行耗时】: {}ms\n" +
                  "【异常类型】: {}\n" +
                  "【异常信息】: {}\n" +
                  "【调用栈】:\n{}",
                requestInfo,
                methodName,
                paramInfo,
                duration.toMillis(),
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
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
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

        if (arg instanceof java.util.Map) {
            return "[Map size=" + ((java.util.Map<?, ?>) arg).size() + "]";
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

    /**
     * 增加调用计数
     */
    private void incrementCallCount(String methodName) {
        METHOD_CALL_COUNT.computeIfAbsent(methodName, k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics(String methodName, long millis, boolean success) {
        METHOD_TOTAL_TIME.computeIfAbsent(methodName, k -> new AtomicLong()).addAndGet(millis);
        
        if (!success) {
            METHOD_ERROR_COUNT.computeIfAbsent(methodName, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * 获取方法调用统计信息
     */
    public String getMethodStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 方法调用统计 ==========\n");

        METHOD_CALL_COUNT.keySet().stream().sorted().forEach(methodName -> {
            long callCount = METHOD_CALL_COUNT.get(methodName).get();
            long errorCount = METHOD_ERROR_COUNT.getOrDefault(methodName, new AtomicLong(0)).get();
            long totalTime = METHOD_TOTAL_TIME.getOrDefault(methodName, new AtomicLong(0)).get();
            long avgTime = callCount > 0 ? totalTime / callCount : 0;
            double errorRate = callCount > 0 ? (double) errorCount / callCount * 100 : 0;

            sb.append(String.format("%-60s [调用:%5d 次] [失败:%5d 次 (%.1f%%)] [平均耗时:%5dms]\n",
                    methodName, callCount, errorCount, errorRate, avgTime));
        });

        sb.append("==================================\n");
        return sb.toString();
    }

    /**
     * 获取慢调用方法列表
     */
    public String getSlowMethods() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 慢调用方法 (平均耗时>").append(slowCallThreshold).append("ms) ==========\n");

        METHOD_CALL_COUNT.keySet().stream().sorted().forEach(methodName -> {
            long callCount = METHOD_CALL_COUNT.get(methodName).get();
            long totalTime = METHOD_TOTAL_TIME.getOrDefault(methodName, new AtomicLong(0)).get();
            long avgTime = callCount > 0 ? totalTime / callCount : 0;

            if (avgTime > slowCallThreshold) {
                sb.append(String.format("%-60s [平均耗时:%dms]\n", methodName, avgTime));
            }
        });

        sb.append("==================================\n");
        return sb.toString();
    }

    /**
     * 获取错误方法列表
     */
    public String getErrorMethods() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 错误方法列表 ==========\n");

        METHOD_CALL_COUNT.keySet().stream().sorted().forEach(methodName -> {
            long errorCount = METHOD_ERROR_COUNT.getOrDefault(methodName, new AtomicLong(0)).get();

            if (errorCount > 0) {
                long callCount = METHOD_CALL_COUNT.get(methodName).get();
                double errorRate = (double) errorCount / callCount * 100;
                sb.append(String.format("%-60s [错误:%5d 次 (%.1f%%)]\n", methodName, errorCount, errorRate));
            }
        });

        sb.append("==================================\n");
        return sb.toString();
    }

    /**
     * 重置统计信息
     */
    public static void resetStatistics() {
        METHOD_CALL_COUNT.clear();
        METHOD_ERROR_COUNT.clear();
        METHOD_TOTAL_TIME.clear();
        log.info("方法调用统计已重置");
    }
}
