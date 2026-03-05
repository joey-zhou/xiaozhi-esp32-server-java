package com.xiaozhi.common.aspect;

import com.xiaozhi.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * 启动方法异常拦截切面
 * 拦截 ApplicationRunner、CommandLineRunner 等启动相关方法
 * <p>
 * 注意：Spring proxy AOP 无法拦截 @PostConstruct 方法（Bean 初始化期间不经过代理），
 * 因此不对 @PostConstruct 进行拦截，避免导致启动失败。
 *
 * @author Joey
 */
//@Aspect
//@Component
public class StartupExceptionAspect {

    private static final Logger log = LogUtils.getLogger(StartupExceptionAspect.class);

    /**
     * 定义切点：ApplicationRunner 接口实现
     */
    @Pointcut("execution(* org.springframework.boot.ApplicationRunner.run(..))")
    public void applicationRunnerMethods() {
    }

    /**
     * 定义切点：CommandLineRunner 接口实现
     */
    @Pointcut("execution(* org.springframework.boot.CommandLineRunner.run(..))")
    public void commandLineRunnerMethods() {
    }

    /**
     * 环绕通知：拦截 ApplicationRunner 和 CommandLineRunner
     */
    @Around("applicationRunnerMethods() || commandLineRunnerMethods()")
    public Object aroundRunner(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = getFullMethodName(joinPoint);
        String runnerType = getRunnerType(joinPoint);
        Instant startTime = Instant.now();

        try {
            log.info("【启动 Runner 开始】{} {}", runnerType, methodName);
            Object result = joinPoint.proceed();
            Duration duration = Duration.between(startTime, Instant.now());
            log.info("【启动 Runner 完成】{} {} [耗时：{}ms]", runnerType, methodName, duration.toMillis());
            return result;
        } catch (Throwable e) {
            Duration duration = Duration.between(startTime, Instant.now());
            handleException(joinPoint, e, runnerType, duration);
            throw e;
        }
    }

    /**
     * 处理异常并记录日志
     */
    private void handleException(ProceedingJoinPoint joinPoint, Throwable e, String type, Duration duration) {
        String methodName = getFullMethodName(joinPoint);
        String paramInfo = getParamInfo(joinPoint);

        log.error("\n========== 启动方法异常 ==========\n" +
                  "【方法类型】: {}\n" +
                  "【方法名称】: {}\n" +
                  "【方法参数】: {}\n" +
                  "【执行耗时】: {}ms\n" +
                  "【异常类型】: {}\n" +
                  "【异常信息】: {}\n" +
                  "【调用栈】:\n{}",
                type,
                methodName,
                paramInfo,
                duration.toMillis(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                buildStackTrace(e),
                e);

        // 启动方法异常需要特别关注，记录到控制台醒目位置
        log.error("\n⚠️⚠️⚠️  启动方法失败，可能导致应用无法正常启动！请检查上方详细日志  ⚠️⚠️⚠️\n");
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
     * 获取 Runner 类型
     */
    private String getRunnerType(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target instanceof ApplicationRunner) {
            return "ApplicationRunner";
        } else if (target instanceof CommandLineRunner) {
            return "CommandLineRunner";
        }
        return "Runner";
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

            if (args[i] instanceof ApplicationArguments) {
                ApplicationArguments appArgs = (ApplicationArguments) args[i];
                sb.append("ApplicationArguments[");
                if (appArgs.getSourceArgs() != null) {
                    sb.append("args=").append(String.join(",", appArgs.getSourceArgs()));
                }
                sb.append("]");
            } else if (args[i] instanceof String[]) {
                String[] strArgs = (String[]) args[i];
                sb.append("String[]=").append(String.join(",", strArgs));
            } else {
                sb.append(args[i].getClass().getSimpleName());
            }
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
