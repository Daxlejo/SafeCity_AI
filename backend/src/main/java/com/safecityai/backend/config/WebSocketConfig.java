package com.safecityai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket STOMP — soporta conexión nativa (wss://) y SockJS (fallback).
 * Endpoint nativo: /ws  →  para StompJs Client (frontend actual)
 * Endpoint SockJS: /ws-sockjs  →  fallback para navegadores legacy
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toArray(String[]::new);

        // WebSocket nativo — para StompJs Client del frontend
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins);

        // SockJS fallback — para navegadores que no soporten WebSocket
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }
}
