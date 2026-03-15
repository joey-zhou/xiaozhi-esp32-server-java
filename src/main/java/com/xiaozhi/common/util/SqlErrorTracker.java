package com.xiaozhi.common.util;

import com.xiaozhi.utils.AuthUtils;
import com.xiaozhi.utils.LogUtils;
import org.slf4j.Logger;

/**
 * SQL 错误追踪器
 * 用于在 SQL 报错时快速定位 API 接口和调用方法
 *
 * @author Joey
 */
public class SqlErrorTracker {

    private static final Logger log = LogUtils.getLogger(SqlErrorTracker.class);

    /**
     * 需要忽略的包名（框架相关）
     */
    private static final String[] IGNORED_PACKAGES = {
            "org.springframework.",
            "org.hibernate.",
            "com.zaxxer.hikari.",
            "org.mariadb.jdbc.",
            "java.",
            "jakarta.",
            "sun.",
            "com.xiaozhi.common.",
            "com.xiaozhi.security.",
            "com.xiaozhi.config."
    };

    /**
     * 格式化 SQL 错误信息，包含 API 接口和调用栈
     */
    public static String formatSqlError(String errorMessage, Throwable cause) {
        StringBuilder sb = new StringBuilder();

        // 1. 添加请求信息
        sb.append("\n========== SQL 错误追踪 ==========\n");
        sb.append("【请求信息】: ").append(AuthUtils.getFullRequestInfo()).append("\n");
        sb.append("【API 路径】: ").append(AuthUtils.getApiPath()).append("\n");

        // 2. 添加错误信息
        sb.append("【错误信息】: ").append(errorMessage).append("\n");

        // 3. 添加调用栈（只追踪到业务代码）
        sb.append("【调用栈】:\n");
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();
        boolean foundBusinessCode = false;

        for (int i = 0; i < stackTraces.length; i++) {
            StackTraceElement element = stackTraces[i];
            String className = element.getClassName();

            // 跳过框架代码，只显示业务代码
            if (isBusinessCode(className)) {
                if (!foundBusinessCode) {
                    sb.append("  ↓ 业务代码调用入口\n");
                    foundBusinessCode = true;
                }
                sb.append("    at ").append(element.toString()).append("\n");
            }
        }

        // 4. 添加 Controller 层信息
        appendControllerInfo(sb, stackTraces);

        sb.append("==================================\n");

        return sb.toString();
    }

    /**
     * 判断是否是业务代码
     */
    private static boolean isBusinessCode(String className) {
        // 只追踪 com.xiaozhi 包下的业务代码（排除 common、security、config）
        if (!className.startsWith("com.xiaozhi.")) {
            return false;
        }
        if (className.startsWith("com.xiaozhi.common.")) {
            return false;
        }
        if (className.startsWith("com.xiaozhi.security.")) {
            return false;
        }
        if (className.startsWith("com.xiaozhi.config.")) {
            return false;
        }
        return true;
    }

    /**
     * 添加 Controller 层信息
     */
    private static void appendControllerInfo(StringBuilder sb, StackTraceElement[] stackTraces) {
        for (StackTraceElement element : stackTraces) {
            String className = element.getClassName();
            if (className.contains(".controller.")) {
                sb.append("【Controller】: ").append(className).append(".")
                        .append(element.getMethodName())
                        .append("(").append(element.getFileName())
                        .append(":").append(element.getLineNumber()).append(")\n");
                break;
            }
        }
    }

    /**
     * 记录 SQL 错误日志
     */
    public static void logSqlError(String sql, Throwable cause) {
        String formattedError = formatSqlError("SQL 执行失败: " + sql, cause);
        log.error(formattedError, cause);
    }

    /**
     * 获取简化的调用信息（用于日志）
     */
    public static String getSimpleCallInfo() {
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stackTraces) {
            String className = element.getClassName();
            if (isBusinessCode(className) && !className.endsWith("Util")) {
                return className + "." + element.getMethodName()
                        + "(" + element.getFileName() + ":" + element.getLineNumber() + ")";
            }
        }
        return "未知调用";
    }
}
