package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Motor de IA para clasificar reportes y calcular trust score.
 * Flujo:
 * 1. Intenta clasificar con OpenRouter (Gemma 3 12B, gratis)
 * 2. Si OpenRouter falla → usa heuristica (reglas fijas) como respaldo
 */
@Slf4j
@Service
public class IAClassificationService {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:google/gemma-3-12b-it:free}")
    private String openRouterModel;

    public IAClassificationService(ReportRepository reportRepository,
                                   NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.notificationService = notificationService;
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
            // Intentar con OpenRouter (Gemma 3 12B gratis)
            result = classifyWithAI(report);
            log.info("[IA] Reporte {} clasificado con OpenRouter/Gemma (score: {})",
                    reportId, result.getTrustScore());
        } catch (Exception e) {
            // FALLBACK: Si OpenRouter falla, usar heuristica
            log.warn("[IA] OpenRouter falló para reporte {}. Razón: {}. Usando heurística.",
                    reportId, e.getMessage());
            result = classifyWithHeuristics(report);
            result.setReasoning("[Fallback heuristico] " + result.getReasoning());
        }

        // Guardar el trust score en el reporte
        report.setTrustScore(result.getTrustScore());
        reportRepository.save(report);

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // MÉTODO @Async — Se ejecuta en un HILO SEPARADO (background)
    // ═══════════════════════════════════════════════════════════════
    //
    // ¿Cómo funciona @Async?
    // ─────────────────────────
    // 1. ReportService.createReport() llama classifyAsync(id)
    // 2. Spring intercepta la llamada y la pone en la cola del
    //    ThreadPool "iaExecutor" (definido en AsyncConfig)
    // 3. createReport() retorna INMEDIATAMENTE al usuario → no espera
    // 4. Cuando hay un hilo libre en el pool, ejecuta este método
    // 5. Si Gemini tarda 5 seg, el usuario NO se entera
    //
    // REGLA IMPORTANTE de @Async:
    // El método @Async DEBE ser llamado desde OTRA clase.
    // Si llamas this.classifyAsync() desde DENTRO de esta clase,
    // Spring NO lo intercepta y se ejecuta SINCRÓNICAMENTE.
    // Por eso ReportService (otra clase) es quien lo llama.
    //
    // @Transactional: necesitamos nuestra propia transacción
    // porque la transacción de createReport() ya terminó.
    //
    @Async("iaExecutor")  // ← Usa el ThreadPool que configuramos
    @Transactional
    public void classifyAsync(Long reportId) {
        log.info("[IA-Async] Iniciando clasificación del reporte ID: {}", reportId);

        try {
            // 1. Clasificar (reutiliza toda la lógica existente)
            IAClassificationDTO result = classifyReport(reportId);

            // 2. Buscar el reporte FRESCO de BD (nueva transacción)
            Report report = reportRepository.findById(reportId).orElse(null);
            if (report == null) {
                log.warn("[IA-Async] Reporte {} no encontrado, posiblemente eliminado", reportId);
                return;
            }

            // 3. Guardar el análisis textual de la IA
            report.setAiAnalysis(result.getReasoning());

            // 4. AUTO-VERIFICAR o AUTO-ELIMINAR basado en el trustScore
            if (result.getTrustScore() >= 50.0) {
                report.setStatus(ReportStatus.VERIFIED);
                log.info("[IA-Async] Reporte {} auto-verificado (score: {})",
                        reportId, result.getTrustScore());
                reportRepository.save(report);
                
                // Notificar al frontend via WebSocket que el reporte fue actualizado
                ReportResponseDTO dto = convertToDTO(report);
                notificationService.notifyReportUpdated(dto);

            } else if (result.getTrustScore() == 0.0) {
                // ELIMINAR COMPLETAMENTE LOS REPORTES BASURA
                log.info("[IA-Async] Reporte {} ELIMINADO por ser basura (score: 0.0)", reportId);
                reportRepository.delete(report);
                notificationService.notifyReportDeleted(reportId);

            } else {
                report.setStatus(ReportStatus.PENDING);
                log.info("[IA-Async] Reporte {} queda PENDING para revisión manual (score: {})",
                        reportId, result.getTrustScore());
                reportRepository.save(report);
                
                ReportResponseDTO dto = convertToDTO(report);
                notificationService.notifyReportUpdated(dto);
            }

        } catch (Exception e) {
            log.error("[IA-Async] Error clasificando reporte {}: {}",
                    reportId, e.getMessage(), e);
        }
    }

    private ReportResponseDTO convertToDTO(Report report) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .description(report.getDescription())
                .incidentType(report.getIncidentType())
                .address(report.getAddress())
                .status(report.getStatus())
                .source(report.getSource())
                .latitude(report.getLatitude())
                .longitude(report.getLongitude())
                .trustScore(report.getTrustScore())
                .aiAnalysis(report.getAiAnalysis())
                .reportDate(report.getReportDate())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // CLASIFICACIÓN CON IA REAL (OpenRouter — Gemma 3 12B)
    // ═══════════════════════════════════════════════════════════════
    //
    // OpenRouter es un servicio que da acceso GRATIS a modelos de IA
    // como Google Gemma 3 12B (12 mil millones de parámetros).
    //
    // API compatible con OpenAI → usa /chat/completions
    // Diferencia clave vs Gemini:
    //   - Gemini:     POST .../generateContent  + body { contents: [...] }
    //   - OpenRouter:  POST .../chat/completions + body { messages: [...] }
    //

    private IAClassificationDTO classifyWithAI(Report report) {
        String prompt = buildPrompt(report);
        String aiResponse = callOpenRouterAPI(prompt);
        return parseOpenRouterResponse(aiResponse, report.getId());
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
        prompt.append("- REGLA CRÍTICA DE GIBBERISH: Si la descripción contiene texto sin sentido, caracteres aleatorios, palabras inventadas, o NO describe un incidente real y específico, el trustScore DEBE SER 0. Ejemplos de gibberish: 'asdfgh', 'jjjjjj', 'hola hola hola', 'test123'.\n");
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
     * Hace la petición HTTP a OpenRouter (API compatible con OpenAI).
     * Endpoint: POST https://openrouter.ai/api/v1/chat/completions
     */
    private String callOpenRouterAPI(String prompt) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        Map<String, Object> body = Map.of(
                "model", openRouterModel,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 500);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openRouterApiKey);
        headers.set("HTTP-Referer", "https://safecityai.onrender.com");
        headers.set("X-Title", "SafeCity AI");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        return response.getBody();
    }

    /**
     * Parsea la respuesta de OpenRouter (formato OpenAI).
     * Estructura: { "choices": [{ "message": { "content": "{ JSON }" } }] }
     */
    private IAClassificationDTO parseOpenRouterResponse(String responseBody, Long reportId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String generatedText = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            generatedText = generatedText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode classification = objectMapper.readTree(generatedText);

            double trustScore = classification.path("trustScore").asDouble(50.0);
            String suggestedTypeStr = classification.path("suggestedType").asText("OTHER");
            String reasoning = classification.path("reasoning").asText("Sin razonamiento disponible");
            boolean shouldVerify = classification.path("shouldVerify").asBoolean(true);

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
                    .reasoning("[IA OpenRouter] " + reasoning)
                    .shouldVerify(shouldVerify)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error parseando respuesta de OpenRouter: " + e.getMessage(), e);
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
        String desc = report.getDescription();

        // ═══ VALIDACIÓN ANTI-GIBBERISH ═══
        // Si el texto es basura, retornamos 0 INMEDIATAMENTE
        // No importa si tiene GPS, foto, etc. → texto sin sentido = score 0
        if (desc == null || desc.isBlank() || isGibberish(desc)) {
            log.info("[Heuristica] Texto detectado como gibberish/vacío: '{}'",
                    desc != null ? desc.substring(0, Math.min(desc.length(), 30)) : "null");
            return 0.0;
        }

        double score = 30.0;

        if (desc.length() > 100)
            score += 25;
        else if (desc.length() > 50)
            score += 15;
        else if (desc.length() > 20)
            score += 5;

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

    // ═══════════════════════════════════════════════════════════════
    // DETECTOR DE GIBBERISH (texto sin sentido)
    // ═══════════════════════════════════════════════════════════════
    //
    // ¿Cómo funciona?
    // Un texto real en español tiene:
    //   1. Proporción de vocales entre 35-55% (ej: "robo a mano armada")
    //   2. Palabras reconocibles (al menos algunas del vocabulario base)
    //   3. Longitud mínima razonable
    //
    // Gibberish como "gjhglyuyuyuyu" tiene:
    //   - Ratio de vocales anormal
    //   - Cero palabras reconocibles
    //   - Caracteres repetidos sin sentido
    //
    private boolean isGibberish(String text) {
        if (text == null || text.isBlank()) return true;

        String clean = text.toLowerCase().replaceAll("[^a-záéíóúñü\\s]", "").trim();
        if (clean.length() < 5) return true;

        // 1. Ratio de vocales — español normal ≈ 40-50%
        long vowels = clean.chars().filter(c -> "aeiouáéíóú".indexOf(c) >= 0).count();
        long letters = clean.chars().filter(Character::isLetter).count();
        if (letters > 0) {
            double ratio = (double) vowels / letters;
            // Si ratio < 15% o > 70% → probablemente gibberish
            if (ratio < 0.15 || ratio > 0.70) return true;
        }

        // 2. Verificar que contenga al menos 1 palabra real en español
        String[] palabrasReales = {
            "robo", "atraco", "hurto", "asalto", "accidente", "choque",
            "moto", "carro", "calle", "avenida", "barrio", "casa",
            "persona", "personas", "hombre", "mujer", "arma", "cuchillo",
            "pistola", "noche", "dia", "fue", "hubo", "hay", "esta",
            "estan", "paso", "ocurrio", "zona", "lugar", "cerca",
            "ayuda", "policia", "herido", "muerto", "sangre",
            "tienda", "banco", "parque", "esquina", "semaforo",
            "transito", "trafico", "vehiculo", "bus", "taxi",
            "peligro", "peligroso", "sospechoso", "robaron", "atacaron",
            "armada", "blanca", "fuego", "disparo", "disparos"
        };

        String lowerText = text.toLowerCase();
        boolean tieneAlMenosUnaPalabraReal = false;
        for (String palabra : palabrasReales) {
            if (lowerText.contains(palabra)) {
                tieneAlMenosUnaPalabraReal = true;
                break;
            }
        }

        // 3. Si no tiene ninguna palabra real Y tiene menos de 30 chars → gibberish
        if (!tieneAlMenosUnaPalabraReal && clean.length() < 30) return true;

        // 4. Detectar caracteres repetidos (ej: "aaaaaa", "jjjjj")
        if (clean.replaceAll("(.)\\1{3,}", "").length() < clean.length() / 2) return true;

        return false;
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
