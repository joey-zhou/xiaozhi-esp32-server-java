package com.xiaozhi.ai.tool;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 交给模型的函数名规范化：只留字母、数字、下划线、连字符与中文（与设备 MCP 工具名同一口径），最长 64。
 * 超长时截断并拼上原名的校验值，两个前缀相同的长名字截断后不会撞成同一个
 */
public final class ToolNames {

    static final int MAX_LENGTH = 64;

    private ToolNames() {
    }

    public static String of(String rawName) {
        String name = rawName.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_");
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        CRC32 crc = new CRC32();
        crc.update(rawName.getBytes(StandardCharsets.UTF_8));
        String suffix = "_" + String.format("%08x", crc.getValue());
        return name.substring(0, MAX_LENGTH - suffix.length()) + suffix;
    }
}
