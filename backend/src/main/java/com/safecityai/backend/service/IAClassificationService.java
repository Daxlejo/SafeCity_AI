package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
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
    private final NotificationUserService notificationUserService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:openai/gpt-oss-120b:free}")
    private String openRouterModel;

    public IAClassificationService(ReportRepository reportRepository,
            NotificationService notificationService,
            NotificationUserService notificationUserService) {
        this.reportRepository = reportRepository;
        this.notificationService = notificationService;
        this.notificationUserService = notificationUserService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ═══════════════════════════════════════════════════════════════
    // METODO PRINCIPAL: clasifica un reporte usando IA real
    // ═══════════════════════════════════════════════════════════════

    public IAClassificationDTO classifyReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new com.safecityai.backend.exception.ResourceNotFoundException("Reporte", "id",
                        reportId));

        IAClassificationDTO result;

        try {
            // Intentar con OpenRouter (Gemma 3)
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
    // ThreadPool "iaExecutor" (definido en AsyncConfig)
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
    @Async("iaExecutor") // ← Usa el ThreadPool que configuramos
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

            // 4. CAMBIAR TIPO si la IA sugiere uno diferente
            IncidentType originalType = report.getIncidentType();
            boolean typeChanged = false;
            if (result.getSuggestedType() != null
                    && !result.getSuggestedType().equals(originalType)) {
                report.setIncidentType(result.getSuggestedType());
                typeChanged = true;
                log.info("[IA-Async] Reporte {} reclasificado: {} → {}",
                        reportId, originalType, result.getSuggestedType());
            }

            // 5. AUTO-VERIFICAR o AUTO-ELIMINAR basado en el trustScore
            User reportOwner = report.getReportedBy();

            if (result.getTrustScore() >= 50.0) {
                report.setStatus(ReportStatus.VERIFIED);
                log.info("[IA-Async] Reporte {} auto-verificado (score: {})",
                        reportId, result.getTrustScore());
                reportRepository.save(report);

                // Notificar al frontend via WebSocket
                ReportResponseDTO dto = convertToDTO(report);
                notificationService.notifyReportUpdated(dto);

                // Notificación persistente al usuario
                if (reportOwner != null) {
                    if (typeChanged) {
                        notificationUserService.createNotification(
                                reportOwner, report,
                                "Reporte reclasificado",
                                "Tu reporte #" + reportId + " fue aceptado pero se reclasificó de "
                                        + originalType + " a " + result.getSuggestedType() + ".",
                                "WARNING");
                    } else {
                        notificationUserService.createNotification(
                                reportOwner, report,
                                "Reporte verificado",
                                "Tu reporte #" + reportId + " fue verificado exitosamente por la IA.",
                                "INFO");
                    }
                }

            } else if (result.getTrustScore() == 0.0) {
                // Notificación ANTES de eliminar (necesitamos la referencia al report)
                if (reportOwner != null) {
                    notificationUserService.createNotification(
                            reportOwner, null,
                            "Reporte rechazado",
                            "Tu reporte #" + reportId + " fue rechazado por la IA por no ser válido.",
                            "ALERT");
                }

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

                // Notificación persistente al usuario
                if (reportOwner != null) {
                    String msg = typeChanged
                            ? "Tu reporte #" + reportId + " está en revisión manual. Se reclasificó de "
                                    + originalType + " a " + result.getSuggestedType() + "."
                            : "Tu reporte #" + reportId + " está pendiente de revisión manual.";
                    notificationUserService.createNotification(
                            reportOwner, report,
                            "Reporte en revisión",
                            msg,
                            typeChanged ? "WARNING" : "INFO");
                }
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
    // - Gemini: POST .../generateContent + body { contents: [...] }
    // - OpenRouter: POST .../chat/completions + body { messages: [...] }
    //

    private IAClassificationDTO classifyWithAI(Report report) {
        String prompt = buildPrompt(report);
        String aiResponse = callOpenRouterAPI(prompt);
        return parseOpenRouterResponse(aiResponse, report.getId());
    }

    private String buildPrompt(Report report) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "Eres un Analista de Veracidad y Clasificación de Incidentes para SafeCityAI en Pasto, Colombia. ");
        prompt.append(
                "Eres un Analista de Veracidad y Clasificación de Incidentes para SafeCityAI en Pasto, Colombia. ");
        prompt.append(
                "Tu objetivo es filtrar reportes falsos y asegurar que la categoría del incidente sea la correcta.\n\n");

        prompt.append("=== DATOS DEL REPORTE ===\n");
        prompt.append("- Descripción del usuario: \"").append(report.getDescription()).append("\"\n");
        prompt.append("- Categoría marcada por usuario: ").append(report.getIncidentType()).append("\n");
        prompt.append("- Coordenadas GPS: ").append(report.getLatitude() != null ? "DISPONIBLES" : "NO DISPONIBLES")
                .append("\n");
        prompt.append("- Evidencia Fotográfica: ").append(report.getPhotoUrl() != null ? "SÍ" : "NO").append("\n\n");

        prompt.append("=== TAREAS CRÍTICAS ===\n");
        prompt.append(
                "1. ANALIZAR COHERENCIA: Si el reporte describe situaciones imposibles (tanques de guerra, aliens, pistolas de agua, superhéroes), el trustScore es 0.\n");
        prompt.append("2. VALIDAR CATEGORÍA: Revisa si la descripción coincide con la categoría marcada. ");
        prompt.append(
                "Si el usuario marcó 'TRAFFIC' pero describe un robo a mano armada, DEBES cambiar 'suggestedType' a 'ROBBERY'.\n");
        prompt.append(
                "Categorías permitidas para 'suggestedType': [ROBBERY, ACCIDENT, TRAFFIC, TRANSIT_OP, OTHER].\n\n");

        prompt.append("=== REGLAS DE PUNTUACIÓN ===\n");
        prompt.append("- REGLA ESTRICTA DE RECHAZO: Si el texto habla de operativos, ruedas de prensa o noticia politica/general y NO describe un incidente especifico, el trustScore DEBE SER 0.\n");
        prompt.append("- Descripción detallada y seria: +30 pts.\n");
        prompt.append("- Datos verificables (GPS/Foto): +30 pts.\n");
        prompt.append("- Reporte vago o sospecha de broma: TrustScore = 0.\n");
        prompt.append("- Si la descripción es puro texto aleatorio (gibberish): TrustScore = 0.\n\n");

        prompt.append("=== FORMATO DE SALIDA (JSON ÚNICAMENTE) ===\n");
        prompt.append("{\n");
        prompt.append("  \"trustScore\": <0-100>,\n");
        prompt.append("  \"suggestedType\": \"<CATEGORIA_CORREGIDA>\",\n");
        prompt.append("  \"reasoning\": \"<Explicación de la puntuación y si se cambió la categoría>\",\n");
        prompt.append("  \"shouldVerify\": <true si score > 50>\n");
        prompt.append("}\n\n");
        prompt.append(
                "INSTRUCCIÓN FINAL: Sé extremadamente escéptico. Si tienes la más mínima duda de que el reporte es falso o una exageración, inclínate por un trustScore menor a 15 y shouldVerify: false.");

        return prompt.toString();
    }

    /**
     * Hace la petición HTTP a OpenRouter con RETRY y FALLBACK.
     *
     * Estrategia anti-rate-limit:
     * 1. Intenta con el modelo principal (Gemma 3 12B)
     * 2. Si da 429 → espera 3 seg → reintenta
     * 3. Si sigue fallando → prueba con modelo alternativo (Llama 3.2 3B)
     * 4. Si todo falla → lanza excepción → cae a la heurística
     */
    private static final String FALLBACK_MODEL = "google/gemma-3-12b-it:free";
    private static final int RETRY_DELAY_MS = 3000;

    private String callOpenRouterAPI(String prompt) {
        // Intento 1: modelo principal
        try {
            return doOpenRouterCall(prompt, openRouterModel);
        } catch (Exception e1) {
            if (!e1.getMessage().contains("429"))
                throw e1;

            log.info("[IA] Modelo {} rate-limited, reintentando en {}ms...",
                    openRouterModel, RETRY_DELAY_MS);
        }

        // Esperar antes de reintentar
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ignored) {
        }

        // Intento 2: mismo modelo después de esperar
        try {
            return doOpenRouterCall(prompt, openRouterModel);
        } catch (Exception e2) {
            if (!e2.getMessage().contains("429"))
                throw e2;

            log.info("[IA] Modelo {} sigue rate-limited, probando fallback: {}",
                    openRouterModel, FALLBACK_MODEL);
        }

        // Intento 3: modelo alternativo (Llama 3.2 3B)
        return doOpenRouterCall(prompt, FALLBACK_MODEL);
    }

    private String doOpenRouterCall(String prompt, String model) {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        Map<String, Object> body = Map.of(
                "model", model,
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

        log.info("[IA] Respuesta exitosa de OpenRouter usando modelo: {}", model);
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
        if (desc == null || desc.isBlank() || isGibberish(desc)) {
            log.info("[Heuristica] Texto detectado como gibberish/vacío: '{}'",
                    desc != null ? desc.substring(0, Math.min(desc.length(), 30)) : "null");
            return 0.0;
        }

        double score = 0.0;

        // 1. Base por fuente (CITIZEN_TEXT=50, INSTITUTIONAL=80, SOCIAL_MEDIA=60, default=30)
        if (report.getSource() != null) {
            switch (report.getSource()) {
                case CITIZEN_TEXT, CITIZEN_VOICE -> score = 50.0;
                case INSTITUTIONAL -> score = 80.0;
                case SOCIAL_MEDIA -> score = 60.0;
                default -> score = 30.0;
            }
        } else {
            score = 30.0;
        }

        // 2. Bonus de +15 si el usuario tiene trustLevel > 70
        if (report.getReportedBy() != null && report.getReportedBy().getTrustLevel() != null) {
            if (report.getReportedBy().getTrustLevel() > 70.0) {
                score += 15.0;
            }
        }

        // 3. Bonus de +10 por cada reporte similar cercano (< 500m, < 2h)
        if (report.getLatitude() != null && report.getLongitude() != null && report.getIncidentType() != null) {
            java.time.LocalDateTime since = (report.getReportDate() != null ? report.getReportDate() : java.time.LocalDateTime.now()).minusHours(2);
            Long excludeId = report.getId() != null ? report.getId() : -1L;
            List<Report> recentReports = reportRepository.findSimilarRecentReports(report.getIncidentType(), since, excludeId);
            
            int nearbyCount = 0;
            for (Report r : recentReports) {
                double distance = calculateHaversineDistance(report.getLatitude(), report.getLongitude(), r.getLatitude(), r.getLongitude());
                if (distance <= 0.5) { // 500 metros = 0.5 km
                    nearbyCount++;
                }
            }
            score += (nearbyCount * 10.0);
        }

        // Cap en 100
        return Math.min(score, 100.0);
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio de la tierra en km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distancia en km
    }

    private boolean isGibberish(String text) {
        if (text == null || text.isBlank())
            return true;

        String clean = text.toLowerCase().replaceAll("[^a-záéíóúñü\\s]", "").trim();
        if (clean.length() < 5)
            return true;

        // 1. Ratio de vocales — español normal ≈ 40-50%
        long vowels = clean.chars().filter(c -> "aeiouáéíóú".indexOf(c) >= 0).count();
        long letters = clean.chars().filter(Character::isLetter).count();
        if (letters > 0) {
            double ratio = (double) vowels / letters;
            // Si ratio < 15% o > 70% → probablemente gibberish
            if (ratio < 0.15 || ratio > 0.70)
                return true;
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
        if (!tieneAlMenosUnaPalabraReal && clean.length() < 30)
            return true;

        // 4. Detectar caracteres repetidos (ej: "aaaaaa", "jjjjj")
        if (clean.replaceAll("(.)\\1{3,}", "").length() < clean.length() / 2)
            return true;

        return false;
    }

    public IncidentType detectIncidentType(String description) {
        if (description == null)
            return IncidentType.OTHER;
        String lower = description.toLowerCase();

        // Análisis riguroso por Keywords según especificación actual
        if (lower.matches(".*\\b(robo|asalto|hurto)\\b.*")) return IncidentType.ROBBERY;
        if (lower.matches(".*\\b(choque|accidente|colision)\\b.*")) return IncidentType.ACCIDENT;
        if (lower.matches(".*\\b(trafico|embotellamiento|via)\\b.*")) return IncidentType.TRAFFIC;
        if (lower.matches(".*\\b(transporte|bus|ruta)\\b.*")) return IncidentType.TRANSIT_OP;

        return IncidentType.OTHER;
    }

    // Exponer el suggestType público
    public IncidentType suggestType(String description) {
        return detectIncidentType(description);
    }

    public Map<String, List<String>> getKeywords() {
        return Map.of(
            "ROBBERY", List.of("robo", "asalto", "hurto"),
            "ACCIDENT", List.of("choque", "accidente", "colision"),
            "TRAFFIC", List.of("trafico", "embotellamiento", "via"),
            "TRANSIT_OP", List.of("transporte", "bus", "ruta")
        );
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
