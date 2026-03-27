package com.safecityai.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de INTEGRACIÓN WebSocket — Sprint 3.
 *
 * Verifica que las notificaciones en tiempo real llegan correctamente
 * a los clientes suscritos a los topics STOMP.
 *
 * ¿Cómo funciona?
 * 1. @SpringBootTest levanta el servidor REAL en un puerto aleatorio
 * 2. Un WebSocketStompClient se conecta al endpoint /ws (igual que el frontend)
 * 3. Se suscribe a un topic (ej: /topic/reports/ALL)
 * 4. Se invoca NotificationService para enviar un mensaje
 * 5. Se verifica que el mensaje llega al cliente suscrito
 *
 * ¿Por qué CompletableFuture?
 * - WebSocket es ASÍNCRONO: el mensaje no llega inmediatamente
 * - CompletableFuture.get(timeout) espera hasta que llegue o expire
 * - Si no llega en 5 segundos → el test FALLA (evita que se quede colgado)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private StompSession session;

    /**
     * Configura el cliente STOMP antes de cada test.
     * Usa SockJS (igual que el frontend con withSockJS()).
     * Se inyecta el ObjectMapper de Spring para que la serialización
     * sea idéntica a la del servidor (respetando @JsonFormat, etc.).
     */
    @BeforeEach
    void setup() throws Exception {
        this.stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );

        // Usar el mismo ObjectMapper que usa Spring Boot (soporta LocalDateTime, @JsonFormat, etc.)
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        this.stompClient.setMessageConverter(converter);

        // Conectar al WebSocket
        String url = "ws://localhost:" + port + "/ws";
        this.session = stompClient
                .connectAsync(url, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * Desconecta la sesión después de cada test para liberar recursos.
     */
    @AfterEach
    void teardown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /**
     * Helper: crea un ReportResponseDTO de ejemplo para usar en los tests.
     * Simula un reporte de ROBO en Pasto, Colombia.
     */
    private ReportResponseDTO buildSampleDTO() {
        return ReportResponseDTO.builder()
                .id(100L)
                .description("Robo reportado cerca al parque Bolívar - Pasto")
                .incidentType(IncidentType.ROBBERY)
                .address("Calle 18 #25-30, Pasto, Nariño")
                .status(ReportStatus.PENDING)
                .source(ReportSource.CITIZEN_TEXT)
                .latitude(1.2136)
                .longitude(-77.2811)
                .reportDate(LocalDateTime.of(2026, 3, 26, 14, 30, 0))
                .build();
    }

    /**
     * Helper: suscribe al topic y retorna un Future que se completa cuando llega el mensaje.
     * Usa byte[] para recibir el payload crudo y luego deserializa manualmente,
     * lo cual evita problemas de conversión de tipos genéricos en STOMP.
     */
    private <T> CompletableFuture<T> subscribeAndListen(String topic, Class<T> type) throws InterruptedException {
        CompletableFuture<T> future = new CompletableFuture<>();

        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, @org.springframework.lang.Nullable Object payload) {
                try {
                    T obj = objectMapper.readValue((byte[]) payload, type);
                    future.complete(obj);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        // Esperar a que la suscripción se registre en el broker
        Thread.sleep(1000);
        return future;
    }

    // ══════════════════════════════════════════════════════════
    //         TEST 1: Nuevo reporte → /topic/reports/ALL
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Nuevo reporte llega a /topic/reports/ALL en tiempo real")
    void testNotifyNewReport() throws Exception {
        // 1. SUSCRIBIRSE al topic global (donde llegan TODOS los reportes nuevos)
        CompletableFuture<ReportResponseDTO> futureMessage =
                subscribeAndListen("/topic/reports/ALL", ReportResponseDTO.class);

        // 2. ENVIAR notificación (esto es lo que haría el ReportController al crear un reporte)
        ReportResponseDTO sampleReport = buildSampleDTO();
        notificationService.notifyNewReport(sampleReport);

        // 3. VERIFICAR que el mensaje llegó al cliente suscrito
        //    .get(5, SECONDS) espera máximo 5 segundos; si no llega, falla el test
        ReportResponseDTO received = futureMessage.get(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.getId()).isEqualTo(100L);
        assertThat(received.getDescription()).contains("Robo reportado");
        assertThat(received.getIncidentType()).isEqualTo(IncidentType.ROBBERY);
        assertThat(received.getLatitude()).isEqualTo(1.2136);
        assertThat(received.getLongitude()).isEqualTo(-77.2811);
        assertThat(received.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    // ══════════════════════════════════════════════════════════
    //     TEST 2: Reporte actualizado → /topic/reports/updated
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Reporte actualizado llega a /topic/reports/updated en tiempo real")
    void testNotifyReportUpdated() throws Exception {
        // 1. SUSCRIBIRSE al topic de actualizaciones
        CompletableFuture<ReportResponseDTO> futureMessage =
                subscribeAndListen("/topic/reports/updated", ReportResponseDTO.class);

        // 2. ENVIAR notificación de actualización (simulando que el reporte fue verificado)
        ReportResponseDTO updatedReport = buildSampleDTO();
        updatedReport.setStatus(ReportStatus.VERIFIED);
        notificationService.notifyReportUpdated(updatedReport);

        // 3. VERIFICAR
        ReportResponseDTO received = futureMessage.get(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.getId()).isEqualTo(100L);
        assertThat(received.getStatus()).isEqualTo(ReportStatus.VERIFIED);
    }

    // ══════════════════════════════════════════════════════════
    //     TEST 3: Reporte eliminado → /topic/reports/deleted
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("ID de reporte eliminado llega a /topic/reports/deleted en tiempo real")
    void testNotifyReportDeleted() throws Exception {
        // 1. SUSCRIBIRSE al topic de eliminaciones
        CompletableFuture<Long> futureMessage =
                subscribeAndListen("/topic/reports/deleted", Long.class);

        // 2. ENVIAR notificación de eliminación
        Long reportIdToDelete = 100L;
        notificationService.notifyReportDeleted(reportIdToDelete);

        // 3. VERIFICAR que llegó el ID correcto
        Long receivedId = futureMessage.get(5, TimeUnit.SECONDS);

        assertThat(receivedId).isEqualTo(100L);
    }
}
