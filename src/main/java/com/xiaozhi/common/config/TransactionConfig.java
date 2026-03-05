package com.xiaozhi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 事务管理器配置
 *
 * @author Joey
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.xiaozhi.repository")
@EnableJpaAuditing
public class TransactionConfig {

    /**
     * 创建主要事务管理器
     * 这样@Transactional 注解就不需要每次都指定 transactionManager
     */
    @Primary
    @Bean("transactionManager")
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
