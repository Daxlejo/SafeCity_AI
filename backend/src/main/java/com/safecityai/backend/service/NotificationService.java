package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Notificaciones en tiempo real con suscripciones selectivas (Patrón Observer).
 *
 * Topics dinámicos por tipo de incidente:
 *   /topic/reports/{TYPE}  → solo ese tipo (ej: /topic/reports/ROBBERY)
 *   /topic/reports/ALL     → todos los eventos
 *   /topic/reports/updated → actualizaciones
 *   /topic/reports/deleted → eliminaciones
 *
 * El frontend se suscribe a los topics que el usuario elija.
 * No se necesita tabla en BD: la suscripción STOMP es la preferencia.
 * Escalable: nuevos IncidentType funcionan automáticamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    private static final String TOPIC_BASE = "/topic/reports";
    private static final String TOPIC_ALL = TOPIC_BASE + "/ALL";
    private static final String TOPIC_UPDATED = TOPIC_BASE + "/updated";
    private static final String TOPIC_DELETED = TOPIC_BASE + "/deleted";

    // Fan-out: envía al canal del tipo específico + canal global
    public void notifyNewReport(ReportResponseDTO report) {
        String typeTopic = buildTypeTopic(report.getIncidentType().name());

        log.info("Notificando nuevo reporte ID: {} → [{}] y [{}]",
                report.getId(), typeTopic, TOPIC_ALL);

        messagingTemplate.convertAndSend(typeTopic, report);
        messagingTemplate.convertAndSend(TOPIC_ALL, report);
    }

    public void notifyReportUpdated(ReportResponseDTO report) {
        log.info("Notificando reporte actualizado ID: {}", report.getId());
        messagingTemplate.convertAndSend(TOPIC_UPDATED, report);
    }

    public void notifyReportDeleted(Long reportId) {
        log.info("Notificando reporte eliminado ID: {}", reportId);
        messagingTemplate.convertAndSend(TOPIC_DELETED, reportId);
    }

    private String buildTypeTopic(String incidentType) {
        return TOPIC_BASE + "/" + incidentType;
    }
}
