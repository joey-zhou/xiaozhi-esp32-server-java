package com.xiaozhi.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 事务管理器配置
 *
 * @author Joey
 */
@Configuration
@EnableJpaAuditing
public class TransactionConfig {

    /**
     * 创建主要事务管理器
     * 这样@Transactional注解就不需要每次都指定transactionManager
     */
//    @Primary
//    @Bean("transactionManager")
//    public PlatformTransactionManager transactionManager(DataSource dataSource) {
//        return new DataSourceTransactionManager(dataSource);
//    }
}