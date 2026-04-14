package com.xiaozhi.ai.tool;

import com.xiaozhi.ai.tool.observation.ToolCallingObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallConfig {

    private final ToolExecutionExceptionProcessor defaultToolExecutionExceptionProcessor
            = DefaultToolExecutionExceptionProcessor.builder().build();

    @Bean
    public ToolCallingManager toolCallingManager(ObservationRegistry observationRegistry,
                                                 @Autowired(required = false) ToolCallbackResolver toolCallbackResolver,
                                                 @Autowired(required = false) ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
        return new XiaoZhiToolCallingManager(observationRegistry,
                toolCallbackResolver != null ? toolCallbackResolver : name -> null,
                toolExecutionExceptionProcessor == null ? defaultToolExecutionExceptionProcessor : toolExecutionExceptionProcessor);
    }

    /**
     * 注册工具调用观察处理器
     * 这个Bean会自动被Spring的ObservationRegistry发现并注册
     */
    @Bean
    public ToolCallingObservationHandler toolCallingObservationHandler() {
        return new ToolCallingObservationHandler();
    }
}
