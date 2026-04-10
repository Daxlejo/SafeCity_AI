package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import org.springframework.stereotype.Service;

/**
 * Motor de IA para clasificar reportes y calcular trust score.
 * 
 * Fase 1 (actual): Algoritmo basado en reglas (heuristico)
 * Fase 2 (futuro): Integrar modelo ML real
 */
@Service
public class IAClassificationService {

    private final ReportRepository reportRepository;

    public IAClassificationService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    // Analiza un reporte, calcula trust score y lo guarda en la BD
    public IAClassificationDTO classifyReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

        double score = calculateTrustScore(report);
        TrustLevel level = scoreTolevel(score);
        IncidentType suggestedType = detectIncidentType(report.getDescription());

        // Guardar el trust score en el reporte
        report.setTrustScore(score);
        reportRepository.save(report);

        return IAClassificationDTO.builder()
                .reportId(reportId)
                .trustScore(score)
                .trustLevel(level)
                .suggestedType(suggestedType)
                .reasoning(generateReasoning(report, score, suggestedType))
                .shouldVerify(score >= 40.0)
                .build();
    }

    /**
     * Calcula el score de confianza (0-100) basado en:
     * - Longitud de la descripcion (mas detalle = mas confianza)
     * - Si tiene coordenadas (ubicacion exacta = mas confiable)
     * - Fuente del reporte (ciudadano directo vs scraping)
     */
    private double calculateTrustScore(Report report) {
        double score = 30.0; // Base

        // Descripcion detallada? (+0 a +25 puntos)
        String desc = report.getDescription();
        if (desc != null) {
            if (desc.length() > 100) score += 25;
            else if (desc.length() > 50) score += 15;
            else if (desc.length() > 20) score += 5;
        }

        // Tiene coordenadas? (+20 puntos)
        if (report.getLatitude() != null && report.getLongitude() != null) {
            score += 20;
        }

        // Tiene direccion? (+10 puntos)
        if (report.getAddress() != null && !report.getAddress().isBlank()) {
            score += 10;
        }

        // Fuente confiable? (+15 puntos si es ciudadano directo)
        if (report.getSource() != null) {
            switch (report.getSource()) {
                case CITIZEN_TEXT -> score += 15;
                case CITIZEN_VOICE -> score += 15;
                case INSTITUTIONAL -> score += 10;
                case SOCIAL_MEDIA -> score += 5;
            }
        }

        // Tiene foto? (+25 puntos - evidencia visual)
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) {
            score += 25;
        }

        return Math.min(score, 100.0);
    }

    // Detecta tipo de incidente basado en palabras clave en la descripcion
    private IncidentType detectIncidentType(String description) {
        if (description == null) return IncidentType.OTHER;

        String lower = description.toLowerCase();

        if (lower.contains("robo") || lower.contains("atraco") || lower.contains("hurto")) {
            return IncidentType.ROBBERY;
        }
        if (lower.contains("accidente") || lower.contains("choque") || lower.contains("colision")) {
            return IncidentType.ACCIDENT;
        }
        if (lower.contains("trafico") || lower.contains("embotellamiento") || lower.contains("via")) {
            return IncidentType.TRAFFIC;
        }
        if (lower.contains("transporte") || lower.contains("bus") || lower.contains("ruta")) {
            return IncidentType.TRANSIT_OP;
        }

        return IncidentType.OTHER;
    }

    private TrustLevel scoreTolevel(double score) {
        if (score >= 80) return TrustLevel.VERIFIED;
        if (score >= 60) return TrustLevel.HIGH;
        if (score >= 40) return TrustLevel.MODERATE;
        if (score >= 20) return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }

    private String generateReasoning(Report report, double score, IncidentType suggested) {
        StringBuilder reason = new StringBuilder();
        reason.append("Score: ").append(String.format("%.1f", score)).append("/100. ");

        if (report.getLatitude() != null) {
            reason.append("Ubicacion GPS proporcionada. ");
        }
        if (report.getDescription() != null && report.getDescription().length() > 50) {
            reason.append("Descripcion detallada. ");
        }
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) {
            reason.append("Foto adjunta (+25 confianza). ");
        }
        if (!suggested.equals(report.getIncidentType())) {
            reason.append("Tipo sugerido difiere del original (").append(suggested).append("). ");
        }

        return reason.toString();
    }
}
