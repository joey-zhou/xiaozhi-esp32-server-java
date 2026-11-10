package com.xiaozhi.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.xiaozhi.utils.DateUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, DateUtils.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, DateUtils.now());
            }

            /**
             * 更新时一律覆盖 updateTime，不能用 strictUpdateFill：它只在字段为空时才填，
             * 而「先查出来再 updateById」拿到的 DO 带着旧值，结果是把旧时间原样写回，
             * 显式 SET 还会压过列上的 ON UPDATE CURRENT_TIMESTAMP。
             * 依赖 updateTime 变化的地方（存储客户端按配置的 updateTime 判断要不要重建、列表按它排序）会因此全部失效。
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.setFieldValByName("updateTime", DateUtils.now(), metaObject);
            }
        };
    }
}
