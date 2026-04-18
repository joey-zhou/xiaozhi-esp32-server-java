package com.xiaozhi.communication.server.websocket;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.xiaozhi.communication.ServerAddressProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // 路径常量已移至 ServerAddressProvider.WS_PATH（xiaozhi-common），保留引用以兼容现有代码
    public static final String WS_PATH = ServerAddressProvider.WS_PATH;

    @Resource
    private WebSocketHandler webSocketHandler;

    @Resource
    private ServerAddressProvider serverAddressProvider;

    @Value("${websocket.max-text-message-buffer-size:65536}")
    private int maxTextMessageBufferSize;

    @Value("${websocket.max-binary-message-buffer-size:1048576}")
    private int maxBinaryMessageBufferSize;

    @Value("${websocket.max-session-idle-timeout:60000}")
    private long maxSessionIdleTimeout;

    @Value("${websocket.async-send-timeout:5000}")
    private long asyncSendTimeout;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, WS_PATH)
                .setAllowedOrigins("*");

        log.info("==========================================================");
        log.info("WebSocket服务地址: {}", serverAddressProvider.getWebsocketAddress());
        log.info("==========================================================");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        container.setMaxSessionIdleTimeout(maxSessionIdleTimeout);
        container.setAsyncSendTimeout(asyncSendTimeout);
        return container;
    }
}