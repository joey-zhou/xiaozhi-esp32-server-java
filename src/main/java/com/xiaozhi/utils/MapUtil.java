package com.xiaozhi.utils;

import java.util.Map;

/**
 * Map 工具类
 *
 * @author Joey
 */
public class MapUtil {

    /**
     * 从 Map 中获取 Integer 值
     *
     * @param map Map
     * @param key 键
     * @return Integer 值，如果不存在或转换失败则返回 null
     */
    public static Integer getInt(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    /**
     * 从 Map 中获取 String 值
     *
     * @param map Map
     * @param key 键
     * @return String 值，如果不存在则返回 null
     */
    public static String getString(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从 Map 中获取 Long 值
     *
     * @param map Map
     * @param key 键
     * @return Long 值，如果不存在或转换失败则返回 null
     */
    public static Long getLong(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    /**
     * 从 Map 中获取 Double 值
     *
     * @param map Map
     * @param key 键
     * @return Double 值，如果不存在或转换失败则返回 null
     */
    public static Double getDouble(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }

    /**
     * 从 Map 中获取 Boolean 值
     *
     * @param map Map
     * @param key 键
     * @return Boolean 值，如果不存在则返回 null
     */
    public static Boolean getBoolean(Map<?, ?> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return null;
        }
        
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        if (value instanceof String) {
            String strValue = (String) value;
            return "true".equalsIgnoreCase(strValue) || "1".equals(strValue);
        }
        
        return null;
    }

    /**
     * 检查 Map 是否包含指定 key 且值不为 null
     *
     * @param map Map
     * @param key 键
     * @return 如果包含且值不为 null 返回 true
     */
    public static boolean hasValue(Map<?, ?> map, String key) {
        return map != null && key != null && map.containsKey(key) && map.get(key) != null;
    }

    /**
     * 生成随机整数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int randomInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min 不能大于 max");
        }
        return (int) (Math.random() * (max - min + 1)) + min;
    }
}
