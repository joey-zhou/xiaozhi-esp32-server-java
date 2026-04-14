package com.xiaozhi.utils;

/**
 * 版本号比较工具类
 */
public class VersionUtils {

    /**
     * 比较两个版本号
     * @param version1 版本1
     * @param version2 版本2
     * @return 如果version1 < version2返回负数，相等返回0，version1 > version2返回正数
     */
    public static int compareVersion(String version1, String version2) {
        if (version1 == null || version2 == null) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? parseVersionPart(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? parseVersionPart(v2Parts[i]) : 0;

            if (v1Part < v2Part) {
                return -1;
            } else if (v1Part > v2Part) {
                return 1;
            }
        }

        return 0;
    }

    /**
     * 解析版本号的每一部分（支持1.2.0-beta这种格式）
     */
    private static int parseVersionPart(String part) {
        try {
            // 如果包含非数字字符（如1.2.0-beta），只取数字部分
            String numericPart = part.replaceAll("[^0-9].*", "");
            return numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
