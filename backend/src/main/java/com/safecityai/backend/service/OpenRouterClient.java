package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.TrustLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente HTTP para comunicarse con la API de OpenRouter.
 *
 * Responsabilidades (SRP — Single Responsibility):
 * ─────────────────────────────────────────────────
 * 1. Construir y enviar peticiones HTTP a OpenRouter
 * 2. Parsear respuestas JSON en DTOs tipados
 * 3. Implementar retry con backoff exponencial ante 429/5xx
 * 4. Cachear resultados por hash de prompt para evitar duplicados
 *
 * Esta clase NO conoce reglas de negocio (qué score es bueno o malo).
 * Esa lógica vive en ReportDecisionEngine.
 */
@Slf4j
@Component
public class OpenRouterClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ═══ Configuración inyectada desde application.properties ═══
    @Value("${app.openrouter.api-key}")
    private String apiKey;

    // ═══ Modelos de IA disponibles (actualizados 26-Abr-2026) ═══
    // Capa 1: Filtro rápido — Google Gemma 3 4B (Multimodal, ~2.3s promedio)
    // Capa 2: Verificador — Google Gemma 3 4B (Reusamos el mismo por alta
    // disponibilidad de la API gratuita)
    // Nota: Llama/Hermes pueden dar 429 por congestión del tier gratuito
    public static final String GEMMA_MODEL = "google/gemma-3-4b-it:free";
    public static final String HERMES_MODEL = "google/gemma-3-4b-it:free";

    // ═══ Constantes de Retry ═══
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 2000;
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";

    // ═══ Caché en memoria (Thread-Safe) ═══
    // Clave: SHA-256 del prompt + modelo → Valor: resultado parseado
    // Evita llamar a la API dos veces con el mismo texto
    private final ConcurrentHashMap<String, IAClassificationDTO> cache = new ConcurrentHashMap<>();

    public OpenRouterClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ═══════════════════════════════════════════════════════════════
    // MÉTODO PÚBLICO: Clasificar con retry + caché
    // ═══════════════════════════════════════════════════════════════

    /**
     * Llama a un modelo de OpenRouter con retry, backoff y caché.
     *
     * @param prompt   El prompt completo para el modelo
     * @param model    Identificador del modelo (GEMMA_MODEL o HERMES_MODEL)
     * @param reportId ID del reporte (para logging)
     * @param label    Etiqueta legible ("Gemma" o "Hermes") para los logs
     * @return DTO con el resultado de la clasificación
     * @throws RuntimeException si todos los reintentos fallan
     */
    public IAClassificationDTO classify(String prompt, String model, Long reportId, String label) {
        // 1. Verificar caché — si ya clasificamos este prompt+modelo, no repetir
        String cacheKey = computeCacheKey(prompt, model);
        IAClassificationDTO cached = cache.get(cacheKey);
        if (cached != null) {
            log.info("[IA-{}] Resultado encontrado en CACHÉ para reporte #{} → score={}",
                    label, reportId, cached.getTrustScore());
            // Clonar con el reportId correcto (puede diferir del original cacheado)
            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(cached.getTrustScore())
                    .trustLevel(cached.getTrustLevel())
                    .suggestedType(cached.getSuggestedType())
                    .reasoning(cached.getReasoning())
                    .shouldVerify(cached.getShouldVerify())
                    .build();
        }

        // 2. Llamar con retry + backoff exponencial
        IAClassificationDTO result = callWithRetry(prompt, model, reportId, label);

        // 3. Guardar en caché (máximo 200 entradas para no consumir memoria)
        if (cache.size() < 200) {
            cache.put(cacheKey, result);
        }

        return result;
    }

    /**
     * Limpia la caché (útil para tests o mantenimiento).
     */
    public void clearCache() {
        cache.clear();
        log.info("[OpenRouterClient] Caché limpiada ({} entradas eliminadas)", cache.size());
    }

    // ═══════════════════════════════════════════════════════════════
    // RETRY CON BACKOFF EXPONENCIAL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Intento 1: llamada directa
     * Intento 2: espera 2s → reintenta
     * Intento 3: espera 4s → reintenta
     * Si todo falla: lanza RuntimeException
     */
    private IAClassificationDTO callWithRetry(String prompt, String model, Long reportId, String label) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1)); // 2s, 4s
                    log.info("[IA-{}] Reintento {}/{} para reporte #{} (esperando {}ms)",
                            label, attempt, MAX_RETRIES, reportId, delay);
                    Thread.sleep(delay);
                }

                log.info("[IA-{}] Clasificando reporte #{} (intento {})", label, reportId, attempt + 1);
                long start = System.currentTimeMillis();
                String response = doHttpCall(prompt, model);
                long elapsed = System.currentTimeMillis() - start;
                log.info("[IA-{}] Respuesta en {}ms para reporte #{}", label, elapsed, reportId);

                return parseResponse(response, reportId);

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRetryable = msg.contains("429") || msg.contains("500")
                        || msg.contains("502") || msg.contains("503") || msg.contains("rate");

                if (!isRetryable || attempt == MAX_RETRIES) {
                    log.error("[IA-{}] Error FINAL clasificando reporte #{}: {}", label, reportId, msg);
                    break;
                }
                log.warn("[IA-{}] Error transitorio reporte #{}: {} → reintentando", label, reportId, msg);
            }
        }

        throw new RuntimeException(
                "[" + label + "] Agotados " + (MAX_RETRIES + 1) + " intentos: " + lastException.getMessage(),
                lastException);
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP — Petición cruda a OpenRouter
    // ═══════════════════════════════════════════════════════════════

    private String doHttpCall(String prompt, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 500);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "https://safecityai.onrender.com");
        headers.set("X-Title", "SafeCity AI");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, String.class);

        log.info("[OpenRouter] Respuesta exitosa de modelo: {}", model);
        return response.getBody();
    }

    // ═══════════════════════════════════════════════════════════════
    // PARSING — JSON de OpenRouter → DTO tipado
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO parseResponse(String responseBody, Long reportId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String generatedText = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            // Limpiar marcadores de código markdown que a veces envuelven la respuesta
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
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Genera una clave de caché usando SHA-256 del prompt + modelo.
     * Dos reportes con la misma descripción producen el mismo hash.
     */
    private String computeCacheKey(String prompt, String model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((model + "::" + prompt).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // Fallback: usar hashCode si SHA-256 no está disponible (nunca debería pasar)
            return model + "::" + prompt.hashCode();
        }
    }

    /** Convierte un score numérico al enum TrustLevel correspondiente. */
    static TrustLevel scoreToLevel(double score) {
        if (score >= 80)
            return TrustLevel.VERIFIED;
        if (score >= 60)
            return TrustLevel.HIGH;
        if (score >= 30)
            return TrustLevel.MODERATE;
        if (score >= 20)
            return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }
}
