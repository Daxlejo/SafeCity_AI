package com.safecityai.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configura el Message Broker.
     * - /topic → canal donde el servidor envía mensajes a los clientes (pub/sub)
     * - /app   → prefijo que usan los clientes para enviar mensajes al servidor
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Habilita un broker simple en memoria para enviar mensajes
        // a los clientes suscritos a destinos que empiecen con /topic
        registry.enableSimpleBroker("/topic");

        // Los mensajes del cliente al servidor deben empezar con /app
        // Ej: cliente envía a "/app/alerta" → llega a @MessageMapping("/alerta")
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registra el endpoint WebSocket donde los clientes se conectan.
     * - /ws → URL de conexión WebSocket
     * - withSockJS() → fallback para navegadores que no soporten WebSocket nativo
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "http://localhost:5173")
                .withSockJS();
    }
}
