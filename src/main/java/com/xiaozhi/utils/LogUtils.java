package com.xiaozhi.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志工具类
 * 替代 Lombok 的 @Slf4j
 *
 * @author Joey
 */
public class LogUtils {

    /**
     * 获取 Logger 实例
     *
     * @param clazz 类
     * @return Logger 实例
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * 获取 Logger 实例
     *
     * @param name Logger 名称
     * @return Logger 实例
     */
    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }

    /**
     * 调试日志
     *
     * @param logger Logger
     * @param message 消息
     * @param args 参数
     */
    public static void debug(Logger logger, String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(format(message, args));
        }
    }

    /**
     * 信息日志
     *
     * @param logger Logger
     * @param message 消息
     * @param args 参数
     */
    public static void info(Logger logger, String message, Object... args) {
        logger.info(format(message, args));
    }

    /**
     * 警告日志
     *
     * @param logger Logger
     * @param message 消息
     * @param args 参数
     */
    public static void warn(Logger logger, String message, Object... args) {
        logger.warn(format(message, args));
    }

    /**
     * 错误日志
     *
     * @param logger Logger
     * @param message 消息
     * @param args 参数
     */
    public static void error(Logger logger, String message, Object... args) {
        logger.error(format(message, args));
    }

    /**
     * 错误日志（带异常）
     *
     * @param logger Logger
     * @param message 消息
     * @param throwable 异常
     * @param args 参数
     */
    public static void error(Logger logger, String message, Throwable throwable, Object... args) {
        logger.error(format(message, args), throwable);
    }

    /**
     * 格式化消息
     *
     * @param message 消息模板
     * @param args 参数
     * @return 格式化后的消息
     */
    private static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int lastIndex = 0;

        while (argIndex < args.length) {
            int placeholderIndex = message.indexOf("{}", lastIndex);
            if (placeholderIndex == -1) {
                break;
            }

            result.append(message, lastIndex, placeholderIndex);
            result.append(String.valueOf(args[argIndex]));
            lastIndex = placeholderIndex + 2;
            argIndex++;
        }

        result.append(message.substring(lastIndex));
        return result.toString();
    }
}
