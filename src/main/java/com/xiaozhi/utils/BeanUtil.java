package com.xiaozhi.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bean 工具类
 * 替代 Hutool 的 BeanUtil
 *
 * @author Joey
 */
@Slf4j
public class BeanUtil {

    // 缓存 BeanInfo，避免重复内省
    private static final Map<Class<?>, BeanInfo> BEAN_INFO_CACHE = new ConcurrentHashMap<>();

    // 缓存属性描述器数组
    private static final Map<Class<?>, PropertyDescriptor[]> PROPERTY_DESCRIPTORS_CACHE = new ConcurrentHashMap<>();


    /**
     * 复制 Bean 属性到另一个 Bean
     *
     * @param source 源 Bean
     * @param target 目标 Bean
     * @param <S>    源 Bean 类型
     * @param <T>    目标 Bean 类型
     * @return 目标 Bean
     */
    public static <S, T> T copyProperties(S source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }

    /**
     * 将投影接口转换为实体类
     * 适用于 Spring Data JPA 的 Interface-based Projection
     *
     * @param projection  投影对象
     * @param entityClass 目标实体类
     * @param <T>         实体类型
     * @return 转换后的实体对象
     */
    public static <T> T projectionToEntity(Object projection, Class<T> entityClass) {
        return projectionToEntity(projection, entityClass, false);
    }

    /**
     * 将投影接口转换为实体类
     * 适用于 Spring Data JPA 的 Interface-based Projection
     *
     * @param projection         投影对象
     * @param entityClass        目标实体类
     * @param copyNullProperties 是否复制 null 值（true-复制 null 值，false-跳过 null 值）
     * @param <T>                实体类型
     * @return 转换后的实体对象
     */
    public static <T> T projectionToEntity(Object projection, Class<T> entityClass, boolean copyNullProperties) {
        if (projection == null) {
            return null;
        }
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(projection, entity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("投影转换为实体失败：" + projection.getClass().getName() + " -> " + entityClass.getName(), e);
        }
    }

    /**
     * 将投影 Page 转换为实体 Page
     *
     * @param projectionPage 投影对象组成的 Page
     * @param entityClass    目标实体类
     * @param <T>            实体类型
     * @return 转换后的实体 Page
     */
    public static <T> Page<T> projectionPageToEntityPage(
            Page<?> projectionPage, Class<T> entityClass) {
        return projectionPage.map(projection ->
                projectionToEntity(projection, entityClass));
    }




}
