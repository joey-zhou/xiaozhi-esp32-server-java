package com.xiaozhi.common.aspect;

import com.xiaozhi.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * 定时任务异常拦截切面
 * 拦截所有标注 @Scheduled 的定时任务方法
 *
 * @author Joey
 */
@Aspect
//@Component
public class ScheduledExceptionAspect {

    private static final Logger log = LogUtils.getLogger(ScheduledExceptionAspect.class);

    /**
     * 定义切点：所有标注 @Scheduled 的方法
     */
    @Pointcut("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public void scheduledMethods() {
    }

    /**
     * 环绕通知：拦截定时任务调用
     */
    @Around("scheduledMethods()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = getFullMethodName(joinPoint);
        String cronInfo = getCronInfo(joinPoint);
        Instant startTime = Instant.now();

        try {
            log.info("【定时任务开始】{} [Cron: {}]", methodName, cronInfo);
            Object result = joinPoint.proceed();
            Duration duration = Duration.between(startTime, Instant.now());
            log.info("【定时任务完成】{} [耗时：{}ms]", methodName, duration.toMillis());
            return result;
        } catch (Throwable e) {
            Duration duration = Duration.between(startTime, Instant.now());
            // 记录异常信息
            handleException(joinPoint, e, duration);
            // 定时任务异常需要重新抛出
            throw e;
        }
    }

    /**
     * 处理异常并记录日志
     */
    private void handleException(ProceedingJoinPoint joinPoint, Throwable e, Duration duration) {
        String methodName = getFullMethodName(joinPoint);
        String cronInfo = getCronInfo(joinPoint);

        log.error("\n========== 定时任务异常 ==========\n" +
                  "【任务方法】: {}\n" +
                  "【Cron 表达式】: {}\n" +
                  "【执行耗时】: {}ms\n" +
                  "【异常类型】: {}\n" +
                  "【异常信息】: {}\n" +
                  "【调用栈】:\n{}",
                methodName,
                cronInfo,
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
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * 获取 Cron 表达式信息
     */
    private String getCronInfo(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        if (scheduled == null) {
            return "未知";
        }

        StringBuilder sb = new StringBuilder();
        if (!scheduled.cron().isEmpty()) {
            sb.append("cron=").append(scheduled.cron());
            if (!scheduled.zone().isEmpty()) {
                sb.append(" zone=").append(scheduled.zone());
            }
        } else if (scheduled.fixedRate() > 0) {
            sb.append("fixedRate=").append(scheduled.fixedRate()).append("ms");
        } else if (scheduled.fixedDelay() > 0) {
            sb.append("fixedDelay=").append(scheduled.fixedDelay()).append("ms");
        }

        return sb.toString();
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
