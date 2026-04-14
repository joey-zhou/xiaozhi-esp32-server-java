package com.xiaozhi.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

public final class MybatisPlusTestHelper {

    private MybatisPlusTestHelper() {
    }

    public static void initTableInfo(Class<?>... entityClasses) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        for (Class<?> entityClass : entityClasses) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                TableInfoHelper.initTableInfo(assistant, entityClass);
            }
        }
    }
}
