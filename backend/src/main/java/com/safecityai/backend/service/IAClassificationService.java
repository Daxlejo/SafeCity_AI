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
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

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

            if (result.getTrustScore() >= 60.0) {
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
        prompt.append("Eres un Analista de Veracidad y Clasificación de Incidentes para SafeCityAI en Pasto, Colombia. ");
        prompt.append("Tu objetivo es determinar si un reporte es real y asignarle la categoría correcta.\n\n");

        // Contexto del reporte
        prompt.append("=== DATOS DEL REPORTE ===\n");
        prompt.append("- Descripción: \"").append(report.getDescription()).append("\"\n");
        prompt.append("- Categoría marcada: ").append(report.getIncidentType()).append("\n");
        prompt.append("- GPS disponible: ").append(report.getLatitude() != null ? "SÍ (ubicación verificada)" : "NO").append("\n");
        prompt.append("- Foto adjunta: ").append(report.getPhotoUrl() != null ? "SÍ" : "NO").append("\n");

        // Reputación del usuario
        if (report.getReportedBy() != null) {
            Double userAvgScore = reportRepository.findAverageTrustScoreByUser(report.getReportedBy().getId());
            if (userAvgScore != null) {
                String repLabel = userAvgScore >= 70 ? "ALTA" : (userAvgScore >= 40 ? "MEDIA" : "BAJA");
                prompt.append("- Reputación histórica del usuario: ").append(repLabel)
                      .append(String.format(" (%.0f%% promedio en reportes anteriores verificados)\n", userAvgScore));
            }
        }

        prompt.append("\n=== TAREAS ===\n");
        prompt.append("1. COHERENCIA: Si el reporte describe situaciones imposibles o es claramente una broma ");
        prompt.append("(aliens, superhéroes, armas de fantasía, reportes tipo 'jaja'), trustScore = 0.\n");
        prompt.append("2. CATEGORÍA: Si la descripción no coincide con la categoría marcada, corrige 'suggestedType'.\n");
        prompt.append("   Categorías válidas: [ROBBERY, ACCIDENT, TRAFFIC, TRANSIT_OP, OTHER].\n\n");

        prompt.append("=== DEFINICIÓN DE CATEGORÍAS ===\n");
        prompt.append("- ROBBERY: robo, atraco, hurto, asalto a mano armada.\n");
        prompt.append("- ACCIDENT: accidente de tránsito con heridos o daños.\n");
        prompt.append("- TRAFFIC: congestión, bloqueo vial, mal estado de vías.\n");
        prompt.append("- TRANSIT_OP: operativo policial, retén, cierre programado.\n");
        prompt.append("- OTHER (USO RESTRINGIDO): SOLO para incendios, inundaciones, derrumbes, ");
        prompt.append("fugas de gas, sismos, emergencias industriales o ambientales graves.\n");
        prompt.append("  ⚠️ OTHER NO aplica para: peleas callejeras sin contexto, quejas de servicios, ");
        prompt.append("ventas, eventos culturales, manifestaciones, política, o contenido mundano.\n\n");

        prompt.append("=== RECHAZO AUTOMÁTICO (trustScore = 0) ===\n");
        prompt.append("- Menciona figuras políticas (presidente, alcalde, Petro, gobernador, senador) como protagonistas del incidente.\n");
        prompt.append("- El reporte es sobre un evento social, cultural o deportivo sin emergencia real.\n");
        prompt.append("- La descripción es claramente ficticia, una broma o texto sin sentido.\n");
        prompt.append("- El contenido es una queja de servicio público (agua, luz, gas) sin emergencia física.\n\n");

        prompt.append("=== NOTA IMPORTANTE SOBRE UBICACIÓN ===\n");
        prompt.append("Si el reporte tiene GPS (coordenadas verificadas), eso ES una ubicación válida, ");
        prompt.append("aunque el texto no mencione una calle específica. No penalices por esto.\n\n");

        prompt.append("=== REGLAS DE PUNTUACIÓN ===\n");
        prompt.append("- Reporte con descripción coherente y seria: puntaje base 50.\n");
        prompt.append("- GPS proporcionado: +15 pts.\n");
        prompt.append("- Descripción detallada (>80 caracteres): +15 pts.\n");
        prompt.append("- Foto adjunta: +15 pts.\n");
        prompt.append("- Usuario con reputación ALTA (>70%): +10 pts extra.\n");
        prompt.append("- Descripción muy vaga (<20 caracteres) pero coherente: -15 pts.\n");
        prompt.append("- Contenido político, mundano, ficticio o broma obvia: trustScore = 0.\n\n");

        prompt.append("=== FORMATO DE RESPUESTA (JSON ÚNICAMENTE, SIN TEXTO ADICIONAL) ===\n");
        prompt.append("{\n");
        prompt.append("  \"trustScore\": <0-100>,\n");
        prompt.append("  \"suggestedType\": \"<CATEGORIA_CORRECTA>\",\n");
        prompt.append("  \"reasoning\": \"<Breve explicación en español>\",\n");
        prompt.append("  \"shouldVerify\": <true si trustScore >= 60>\n");
        prompt.append("}\n");

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

        // Si el texto es basura → score 0 inmediato
        if (desc == null || desc.isBlank() || isGibberish(desc)) {
            log.info("[Heuristica] Texto detectado como gibberish/vacío: '{}'",
                    desc != null ? desc.substring(0, Math.min(desc.length(), 30)) : "null");
            return 0.0;
        }

        // Puntaje base para cualquier reporte coherente
        double score = 50.0;

        // Longitud de la descripción
        if (desc.length() > 100)
            score += 15;
        else if (desc.length() > 50)
            score += 10;
        else if (desc.length() < 20)
            score -= 15; // muy vaga

        // GPS
        if (report.getLatitude() != null && report.getLongitude() != null)
            score += 15;

        // Foto
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank())
            score += 15;

        // Fuente
        if (report.getSource() != null) {
            switch (report.getSource()) {
                case CITIZEN_TEXT -> score += 5;
                case CITIZEN_VOICE -> score += 5;
                case INSTITUTIONAL -> score += 10;
                case SOCIAL_MEDIA -> { } // sin bonus
            }
        }

        // ═══ REPUTACIÓN HISTÓRICA DEL USUARIO ═══
        // Si el usuario tiene un buen historial, le damos más confianza
        if (report.getReportedBy() != null) {
            Double userAvgScore = reportRepository.findAverageTrustScoreByUser(report.getReportedBy().getId());
            if (userAvgScore != null) {
                if (userAvgScore >= 70)
                    score += 10; // usuario confiable
                else if (userAvgScore < 30)
                    score -= 10; // usuario con historial pobre
            }
        }

        return Math.min(Math.max(score, 0.0), 100.0);
    }

    // ═══════════════════════════════════════════════════════════════
    // DETECTOR DE GIBBERISH (texto sin sentido)
    // ═══════════════════════════════════════════════════════════════
    //
    // ¿Cómo funciona?
    // Un texto real en español tiene:
    // 1. Proporción de vocales entre 35-55% (ej: "robo a mano armada")
    // 2. Palabras reconocibles (al menos algunas del vocabulario base)
    // 3. Longitud mínima razonable
    //
    // Gibberish como "gjhglyuyuyuyu" tiene:
    // - Ratio de vocales anormal
    // - Cero palabras reconocibles
    // - Caracteres repetidos sin sentido
    //
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
