package com.safecityai.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Sprint 3 - Edgar
 * Manejador de eventos de conexión WebSocket para auditoría y seguimiento.
 * Permite registrar cuándo los clientes se conectan y desconectan para recibir reportes.
 */
@Slf4j
@Component
public class ReportWebSocketHandler {

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("Nuevo cliente conectado a WebSocket. Session ID: {}", headerAccessor.getSessionId());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("Cliente desconectado de WebSocket. Session ID: {}", headerAccessor.getSessionId());
    }
}
