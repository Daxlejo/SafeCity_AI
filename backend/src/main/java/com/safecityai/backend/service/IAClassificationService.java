package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Motor de IA para clasificar reportes y calcular trust score.
 * Flujo:
 * 1. Intenta clasificar con Gemini
 * 2. Si Gemini falla → usa heuristica (reglas fijas) como respaldo
 */
@Service
public class IAClassificationService {

    private final ReportRepository reportRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    public IAClassificationService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ═══════════════════════════════════════════════════════════════
    // METODO PRINCIPAL: clasifica un reporte usando IA real
    // ═══════════════════════════════════════════════════════════════

    public IAClassificationDTO classifyReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

        IAClassificationDTO result;

        try {
            // FASE 2: Intentar con Gemini
            result = classifyWithGemini(report);
        } catch (Exception e) {
            // FALLBACK: Si Gemini falla, usar heuristica
            result = classifyWithHeuristics(report);
            result.setReasoning("[Fallback heuristico] " + result.getReasoning());
        }

        // Guardar el trust score en el reporte
        report.setTrustScore(result.getTrustScore());
        reportRepository.save(report);

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // FASE 2: Clasificacion con Gemini
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO classifyWithGemini(Report report) {
        String prompt = buildPrompt(report);
        String geminiResponse = callGeminiAPI(prompt);
        return parseGeminiResponse(geminiResponse, report.getId());
    }

    private String buildPrompt(Report report) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres un experto en seguridad ciudadana y analisis de incidentes en Colombia. ");
        prompt.append(
                "Analiza el siguiente reporte ciudadano y devuelve UNICAMENTE un JSON valido (sin markdown, sin explicaciones, solo el JSON).\n\n");

        prompt.append("=== DATOS DEL REPORTE ===\n");
        prompt.append("Descripcion: ").append(report.getDescription()).append("\n");
        prompt.append("Tipo reportado: ").append(report.getIncidentType()).append("\n");
        prompt.append("Direccion: ").append(report.getAddress() != null ? report.getAddress() : "No proporcionada")
                .append("\n");
        prompt.append("Tiene GPS: ").append(report.getLatitude() != null ? "Si" : "No").append("\n");
        prompt.append("Tiene foto: ").append(report.getPhotoUrl() != null ? "Si" : "No").append("\n");
        prompt.append("Fuente: ").append(report.getSource()).append("\n\n");

        prompt.append("=== RESPONDE CON ESTE JSON EXACTO ===\n");
        prompt.append("{\n");
        prompt.append("  \"trustScore\": <numero entre 0 y 100>,\n");
        prompt.append(
                "  \"suggestedType\": \"<uno de: ROBBERY, ASSAULT, VANDALISM, ACCIDENT, TRAFFIC, TRANSIT_OP, OTHER>\",\n");
        prompt.append("  \"reasoning\": \"<explicacion breve en espanol de por que diste ese puntaje>\",\n");
        prompt.append("  \"shouldVerify\": <true si el score es mayor a 40>\n");
        prompt.append("}\n\n");

        prompt.append("REGLAS de puntuación:\n");
        prompt.append("- REGLA ESTRICTA DE RECHAZO: Si el texto habla de operativos de control preventivos, ruedas de prensa, captura de hace tiempo, o es una noticia politica/general y NO describe un incidente especifico ocurriendo, el trustScore DEBE SER EXACTAMENTE 0.\n");
        prompt.append("- Descripcion detallada y coherente: +20 a +30 puntos\n");
        prompt.append("- Tiene coordenadas GPS: +15 puntos\n");
        prompt.append("- Tiene foto adjunta: +20 puntos\n");
        prompt.append("- Fuente confiable (ciudadano directo): +10 puntos\n");
        prompt.append("- Descripcion vaga o poco clara: -10 a -30 puntos\n");
        prompt.append("- Base de partida para incidentes reales: 30 puntos\n");

        return prompt.toString();
    }

    /**
     * Hace la peticion HTTP a la API de Google Gemini.
     * Endpoint: POST /v1beta/models/{model}:generateContent
     */
    private String callGeminiAPI(String prompt) {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                geminiModel, geminiApiKey);

        // Construir el body que Gemini espera
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 500));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        return response.getBody();
    }

    /**
     * Parsea la respuesta de Gemini y extrae el JSON con la clasificacion.
     * La respuesta de Gemini viene asi:
     * { "candidates": [{ "content": { "parts": [{ "text": "{ trustScore: 75 ... }"
     * }] } }] }
     */
    private IAClassificationDTO parseGeminiResponse(String responseBody, Long reportId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String generatedText = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // Limpia el texto
            generatedText = generatedText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode classification = objectMapper.readTree(generatedText);

            double trustScore = classification.path("trustScore").asDouble(50.0);
            String suggestedTypeStr = classification.path("suggestedType").asText("OTHER");
            String reasoning = classification.path("reasoning").asText("Sin razonamiento disponible");
            boolean shouldVerify = classification.path("shouldVerify").asBoolean(true);

            // Convertir el tipo sugerido
            IncidentType suggestedType;
            try {
                suggestedType = IncidentType.valueOf(suggestedTypeStr);
            } catch (IllegalArgumentException e) {
                suggestedType = IncidentType.OTHER;
            }

            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(trustScore)
                    .trustLevel(scoreToLevel(trustScore))
                    .suggestedType(suggestedType)
                    .reasoning("[Gemini AI] " + reasoning)
                    .shouldVerify(shouldVerify)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error parseando respuesta de Gemini: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FASE 1 (FALLBACK): Clasificacion con reglas fijas
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO classifyWithHeuristics(Report report) {
        double score = calculateTrustScore(report);
        TrustLevel level = scoreToLevel(score);
        IncidentType suggestedType = detectIncidentType(report.getDescription());

        return IAClassificationDTO.builder()
                .reportId(report.getId())
                .trustScore(score)
                .trustLevel(level)
                .suggestedType(suggestedType)
                .reasoning(generateReasoning(report, score, suggestedType))
                .shouldVerify(score >= 40.0)
                .build();
    }

    private double calculateTrustScore(Report report) {
        double score = 30.0;

        String desc = report.getDescription();
        if (desc != null) {
            if (desc.length() > 100)
                score += 25;
            else if (desc.length() > 50)
                score += 15;
            else if (desc.length() > 20)
                score += 5;
        }

        if (report.getLatitude() != null && report.getLongitude() != null) {
            score += 20;
        }
        if (report.getAddress() != null && !report.getAddress().isBlank()) {
            score += 10;
        }
        if (report.getSource() != null) {
            switch (report.getSource()) {
                case CITIZEN_TEXT -> score += 15;
                case CITIZEN_VOICE -> score += 15;
                case INSTITUTIONAL -> score += 10;
                case SOCIAL_MEDIA -> score += 5;
            }
        }
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) {
            score += 25;
        }

        return Math.min(score, 100.0);
    }

    private IncidentType detectIncidentType(String description) {
        if (description == null)
            return IncidentType.OTHER;
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

    private TrustLevel scoreToLevel(double score) {
        if (score >= 80)
            return TrustLevel.VERIFIED;
        if (score >= 60)
            return TrustLevel.HIGH;
        if (score >= 40)
            return TrustLevel.MODERATE;
        if (score >= 20)
            return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }

    private String generateReasoning(Report report, double score, IncidentType suggested) {
        StringBuilder reason = new StringBuilder();
        reason.append("Score: ").append(String.format("%.1f", score)).append("/100. ");
        if (report.getLatitude() != null)
            reason.append("Ubicacion GPS proporcionada. ");
        if (report.getDescription() != null && report.getDescription().length() > 50)
            reason.append("Descripcion detallada. ");
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank())
            reason.append("Foto adjunta (+25 confianza). ");
        if (!suggested.equals(report.getIncidentType())) {
            reason.append("Tipo sugerido difiere del original (").append(suggested).append("). ");
        }
        return reason.toString();
    }
}
