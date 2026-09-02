package com.xiaozhi.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

public final class MybatisPlusTestHelper {

    private MybatisPlusTestHelper() {
    }

    public static void initTableInfo(Class<?>... entityClasses) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        // MybatisConfiguration 默认把 mapUnderscoreToCamelCase 打开，会把 userId 映射成 user_id 列；
        // 生产 application.yml 里 mybatis-plus.configuration.map-underscore-to-camel-case 为 false，
        // 库表列名本身就是驼峰，这里必须显式对齐，否则 targetSql 断言钉的是与生产不同的列名。
        configuration.setMapUnderscoreToCamelCase(false);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityClass : entityClasses) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                TableInfoHelper.initTableInfo(assistant, entityClass);
            }
        }
    }
}
