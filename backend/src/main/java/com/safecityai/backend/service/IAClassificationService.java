package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationUserService notificationUserService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Puntos de reputación
    private static final double PENALTY_REJECTED = 5.0;  // -5 por reporte rechazado (score 0)
    private static final double BONUS_VERIFIED = 2.0;    // +2 por reporte verificado (score ≥ 60)

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:openai/gpt-oss-120b:free}")
    private String openRouterModel;

    public IAClassificationService(ReportRepository reportRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            NotificationUserService notificationUserService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
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

                // BONUS: recompensar al usuario por buen reporte (+2 trustLevel)
                if (reportOwner != null) {
                    adjustTrustLevel(reportOwner, BONUS_VERIFIED);
                    String msg;
                    if (typeChanged) {
                        msg = "Tu reporte #" + reportId + " fue aceptado pero se reclasificó de "
                                + originalType + " a " + result.getSuggestedType()
                                + ". +" + (int) BONUS_VERIFIED + " puntos de reputación.";
                        notificationUserService.createNotification(
                                reportOwner, report, "Reporte reclasificado", msg, "WARNING");
                    } else {
                        msg = "Tu reporte #" + reportId + " fue verificado exitosamente. +"
                                + (int) BONUS_VERIFIED + " puntos de reputación.";
                        notificationUserService.createNotification(
                                reportOwner, report, "Reporte verificado ✅", msg, "INFO");
                    }
                }

            } else if (result.getTrustScore() == 0.0) {
                // PENALIZACIÓN: restar trustLevel al usuario por reporte basura
                if (reportOwner != null) {
                    adjustTrustLevel(reportOwner, -PENALTY_REJECTED);
                    String reason = result.getReasoning() != null ? result.getReasoning() : "contenido no válido";
                    notificationUserService.createNotification(
                            reportOwner, null,
                            "⚠️ Reporte rechazado",
                            "Tu reporte #" + reportId + " fue rechazado: " + reason
                                    + ". Perdiste " + (int) PENALTY_REJECTED + " puntos de reputación.",
                            "ALERT");
                    log.info("[IA-Async] Usuario {} penalizado -{} trustLevel por reporte rechazado #{}",
                            reportOwner.getId(), (int) PENALTY_REJECTED, reportId);
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

    /**
     * Ajusta el trustLevel del usuario y lo guarda en BD.
     * Clamped a [0, 100] para no salirse de rango.
     */
    private void adjustTrustLevel(User user, double delta) {
        double current = user.getTrustLevel() != null ? user.getTrustLevel() : 50.0;
        double newLevel = Math.max(0, Math.min(100, current + delta));
        user.setTrustLevel(newLevel);
        userRepository.save(user);
        log.info("[TrustLevel] Usuario {} ajustado: {} → {} (delta: {})",
                user.getId(), (int) current, (int) newLevel, (delta >= 0 ? "+" : "") + (int) delta);
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
        prompt.append("Eres el sistema de filtrado de SafeCityAI en Pasto, Colombia. ");
        prompt.append("Evalúas reportes ciudadanos de seguridad en DOS FASES OBLIGATORIAS Y SECUENCIALES. ");
        prompt.append("Nunca te saltes la FASE 1 ni mezcles las fases.\n\n");
        prompt.append(
                "Tambien debes asignarle la categoría correcta al reporte si no coincide con la categoría marcada.\n\n");

        // Contexto del reporte
        prompt.append("=== DATOS DEL REPORTE ===\n");
        prompt.append("- Descripción: \"").append(report.getDescription()).append("\"\n");
        prompt.append("- Categoría marcada: ").append(report.getIncidentType()).append("\n");
        prompt.append("- GPS disponible: ").append(report.getLatitude() != null ? "SÍ (ubicación verificada)" : "NO")
                .append("\n");
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

        // --- FASE 1: CORTOCIRCUITO ABSOLUTO ---
        prompt.append("=== FASE 1: FILTRO ABSOLUTO (ejecutar PRIMERO, antes de calcular nada) ===\n");
        prompt.append("Si el reporte contiene CUALQUIERA de estos elementos, responde INMEDIATAMENTE ");
        prompt.append("con trustScore=0 y NO calcules puntos. No hay excepciones.\n\n");
        prompt.append("Señales de rechazo inmediato:\n");
        prompt.append("- Leyendas o seres sobrenaturales: 'la mano peluda', 'el coco', 'la llorona', ");
        prompt.append("'el duende', 'el diablo', fantasmas, brujas, o cualquier entidad ficticia.\n");
        prompt.append(
                "- Jerga de internet o burla: 'jajaja', 'xd', 'lol', 'lmao', 'me cayó el veinte', emojis de risa.\n");
        prompt.append(
                "- Imposibilidades físicas: 'mil muertos', 'el bus explotó y nadie murió', exageraciones absurdas.\n");
        prompt.append("- Insultos, groserías o texto incoherente.\n");
        prompt.append("- Queja de servicios públicos: agua, luz, internet, basuras, baches.\n");
        prompt.append("- Contenido político o de opinión personal.\n\n");
        prompt.append("REGLA CRÍTICA DE FASE 1: La presencia de GPS, foto o cuenta verificada NO rescata ");
        prompt.append("un reporte que dispara este filtro. Un reporte con GPS que dice ");
        prompt.append("'la mano peluda me robó' SIEMPRE es trustScore=0.\n\n");

        // --- FASE 2: PUNTUACIÓN ---
        prompt.append("=== FASE 2: PUNTUACIÓN (solo si el reporte pasó FASE 1 limpiamente) ===\n");
        prompt.append("Puntaje base: 20 puntos (el reporte existe y no es basura obvia).\n");
        prompt.append("Suma SOLO si el reporte es serio y describe un evento de seguridad real:\n");
        prompt.append("+ Descripción seria, coherente y útil para una autoridad: +30 pts.\n");
        prompt.append("+ Detalles específicos (placas, descripción física del agresor, dirección exacta): +20 pts.\n");
        prompt.append("+ GPS verificado (coordenadas presentes): +15 pts.\n");
        prompt.append("+ Foto adjunta y relevante: +15 pts.\n\n");
        prompt.append("Penalizaciones (aplicar después de sumar):\n");
        prompt.append("- Descripción demasiado vaga, ej: solo 'me robaron': -30 pts.\n");
        prompt.append("- Reporte de segunda mano o rumor ('me dijeron que...'): -15 pts.\n\n");

        // --- CATEGORÍAS ---
        prompt.append("=== CATEGORÍAS VÁLIDAS (IncidentType) ===\n");
        prompt.append("ROBBERY: Robos, atracos, hurtos activos o recientes.\n");
        prompt.append("ACCIDENT: Choques, atropellos, incidentes viales con daños o heridos.\n");
        prompt.append("TRAFFIC: Trancón pesado, semáforos dañados, vías bloqueadas.\n");
        prompt.append("TRANSIT_OP: Retenes, operativos de tránsito, cierres viales oficiales.\n");
        prompt.append("OTHER: Emergencias físicas únicamente: incendio, inundación, derrumbe, fuga de gas.\n\n");

        // --- FORMATO DE SALIDA ---
        prompt.append("=== FORMATO DE RESPUESTA: JSON puro, sin texto antes ni después ===\n");
        prompt.append("{\n");
        prompt.append("  \"trustScore\": <0-100>,\n");
        prompt.append("  \"suggestedType\": \"<ROBBERY|ACCIDENT|TRAFFIC|TRANSIT_OP|OTHER>\",\n");
        prompt.append(
                "  \"reasoning\": \"<Si trustScore=0 por FASE 1: cita la frase exacta que activó el filtro y explica por qué. Si es válido: describe qué información útil aporta el reporte.>\",\n");
        prompt.append("  \"shouldVerify\": <true si trustScore >= 70, false en caso contrario>\n");
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
                case SOCIAL_MEDIA -> {
                } // sin bonus
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
