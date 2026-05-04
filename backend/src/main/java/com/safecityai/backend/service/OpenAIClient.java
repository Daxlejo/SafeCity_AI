package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente de IA para la API de OpenAI (compatible con OpenRouter y endpoints OpenAI-like).
 *
 * Responsabilidades (SRP):
 * ────────────────────────
 * 1. Construir y enviar peticiones HTTP (texto y multimodal)
 * 2. Parsear respuestas JSON en DTOs tipados
 * 3. Implementar retry con backoff exponencial ante 429/5xx
 * 4. Cachear resultados por hash de prompt para evitar duplicados
 *
 * Polimorfismo: La URL y modelo son configurables vía application.properties.
 * Cambiar de OpenAI a OpenRouter (o cualquier API compatible) es solo cambiar config.
 */
@Slf4j
@Component
public class OpenAIClient implements AIClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 2000;

    // Caché thread-safe: SHA-256(prompt+model) → resultado
    private final ConcurrentHashMap<String, IAClassificationDTO> classificationCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;

    public OpenAIClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[AIClient] ⚠️ API Key not configured (app.ai.api-key). " +
                    "AI classification will NOT work (fallback to heuristics).");
        } else {
            String prefix = apiKey.length() > 15 ? apiKey.substring(0, 15) + "..." : "***";
            log.info("[AIClient] ✅ Configured — model: {}, endpoint: {}, key: {}",
                    model, apiUrl, prefix);
        }
    }

    @Override
    public String getModelId() {
        return model;
    }

    // ═══════════════════════════════════════════════════════════════
    // MULTIMODAL CLASSIFICATION — System + User (text + image)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public IAClassificationDTO classifyMultimodal(
            String systemPrompt,
            List<Map<String, Object>> userContent,
            Long reportId) {

        // 1. Check cache
        String cacheKey = computeCacheKey(systemPrompt + userContent.toString());
        IAClassificationDTO cached = classificationCache.get(cacheKey);
        if (cached != null) {
            log.info("[AIClient] Cache HIT for report #{} → score={}", reportId, cached.getTrustScore());
            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(cached.getTrustScore())
                    .trustLevel(cached.getTrustLevel())
                    .suggestedType(cached.getSuggestedType())
                    .reasoning(cached.getReasoning())
                    .statusDecision(cached.getStatusDecision())
                    .shouldVerify(cached.getShouldVerify())
                    .build();
        }

        // 2. Call API with retry
        IAClassificationDTO result = callMultimodalWithRetry(systemPrompt, userContent, reportId);

        // 3. Store in cache
        if (classificationCache.size() < MAX_CACHE_SIZE) {
            classificationCache.put(cacheKey, result);
        }

        return result;
    }

    private IAClassificationDTO callMultimodalWithRetry(
            String systemPrompt,
            List<Map<String, Object>> userContent,
            Long reportId) {

        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    log.info("[AIClient] Retry {}/{} for report #{} (waiting {}ms)",
                            attempt, MAX_RETRIES, reportId, delay);
                    Thread.sleep(delay);
                }

                log.info("[AIClient] Classifying report #{} (attempt {}, model: {})",
                        reportId, attempt + 1, model);
                long start = System.currentTimeMillis();

                String responseBody = doMultimodalHttpCall(systemPrompt, userContent);
                long elapsed = System.currentTimeMillis() - start;
                log.info("[AIClient] Response in {}ms for report #{}", elapsed, reportId);

                return parseClassificationResponse(responseBody, reportId);

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                if (msg.contains("401") || (e instanceof HttpClientErrorException.Unauthorized)) {
                    log.error("[AIClient] ❌ 401 Unauthorized for report #{}. " +
                            "Check app.ai.api-key configuration.", reportId);
                    break;
                }

                boolean isRetryable = msg.contains("429") || msg.contains("500")
                        || msg.contains("502") || msg.contains("503") || msg.contains("rate");

                if (!isRetryable || attempt == MAX_RETRIES) {
                    log.error("[AIClient] Final error for report #{}: {}", reportId, msg);
                    break;
                }
                log.warn("[AIClient] Transient error for report #{}: {} → retrying", reportId, msg);
            }
        }

        throw new RuntimeException(
                "[AIClient] Exhausted " + (MAX_RETRIES + 1) + " attempts for report: "
                        + lastException.getMessage(), lastException);
    }

    // ═══════════════════════════════════════════════════════════════
    // RAW PROMPT — Free-form text (used by OSINT)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String sendRawPrompt(String prompt, String label) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    log.info("[{}] Retry {}/{} (waiting {}ms)", label, attempt, MAX_RETRIES, delay);
                    Thread.sleep(delay);
                }

                long start = System.currentTimeMillis();
                String response = doTextHttpCall(prompt);
                long elapsed = System.currentTimeMillis() - start;
                log.info("[{}] Response in {}ms (model: {})", label, elapsed, model);

                return extractContentFromResponse(response);

            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                if (msg.contains("401") || (e instanceof HttpClientErrorException.Unauthorized)) {
                    log.error("[{}] 401 Unauthorized — invalid API key", label);
                    break;
                }

                boolean isRetryable = msg.contains("429") || msg.contains("500")
                        || msg.contains("502") || msg.contains("503") || msg.contains("rate");
                if (!isRetryable || attempt == MAX_RETRIES) {
                    log.error("[{}] Final error: {}", label, msg);
                    break;
                }
                log.warn("[{}] Transient error: {} — retrying", label, msg);
            }
        }

        throw new RuntimeException(
                "[" + label + "] Exhausted " + (MAX_RETRIES + 1) + " attempts: "
                        + lastException.getMessage(), lastException);
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP — Multimodal request (system + user with content array)
    // ═══════════════════════════════════════════════════════════════

    private String doMultimodalHttpCall(String systemPrompt, List<Map<String, Object>> userContent) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // User message with content array (text + optional image)
        messages.add(Map.of("role", "user", "content", userContent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.2);
        body.put("max_tokens", 600);

        return executeHttpRequest(body);
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP — Simple text request (for OSINT raw prompts)
    // ═══════════════════════════════════════════════════════════════

    private String doTextHttpCall(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3,
                "max_tokens", 500);

        return executeHttpRequest(body);
    }

    // ═══════════════════════════════════════════════════════════════
    // HTTP — Shared request execution
    // ═══════════════════════════════════════════════════════════════

    private String executeHttpRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, request, String.class);

        log.debug("[AIClient] Successful response from {}", apiUrl);
        return response.getBody();
    }

    // ═══════════════════════════════════════════════════════════════
    // PARSING — Classification JSON → DTO
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO parseClassificationResponse(String responseBody, Long reportId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String generatedText = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content").asText();

            // Clean markdown code fences that models sometimes wrap around JSON
            generatedText = generatedText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode classification = objectMapper.readTree(generatedText);

            double trustScore = classification.path("trustScore").asDouble(50.0);
            String suggestedTypeStr = classification.path("suggestedType").asText("OTHER");
            String reasoning = classification.path("reasoning").asText("No reasoning provided");
            String statusDecisionStr = classification.path("statusDecision").asText("PENDING");

            IncidentType suggestedType;
            try {
                suggestedType = IncidentType.valueOf(suggestedTypeStr);
            } catch (IllegalArgumentException e) {
                suggestedType = IncidentType.OTHER;
            }

            ReportStatus statusDecision;
            try {
                statusDecision = ReportStatus.valueOf(statusDecisionStr);
            } catch (IllegalArgumentException e) {
                statusDecision = ReportStatus.PENDING;
            }

            // Derive shouldVerify from statusDecision for backward compatibility
            boolean shouldVerify = statusDecision == ReportStatus.VERIFIED;

            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(trustScore)
                    .trustLevel(scoreToLevel(trustScore))
                    .suggestedType(suggestedType)
                    .reasoning("[IA " + model + "] " + reasoning)
                    .statusDecision(statusDecision)
                    .shouldVerify(shouldVerify)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error parsing AI classification response: " + e.getMessage(), e);
        }
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();
            return content.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            throw new RuntimeException("Error extracting content from AI response: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    public void clearCache() {
        int size = classificationCache.size();
        classificationCache.clear();
        log.info("[AIClient] Cache cleared ({} entries removed)", size);
    }

    private String computeCacheKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((model + "::" + input).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return model + "::" + input.hashCode();
        }
    }

    static TrustLevel scoreToLevel(double score) {
        if (score >= 80) return TrustLevel.VERIFIED;
        if (score >= 60) return TrustLevel.HIGH;
        if (score >= 30) return TrustLevel.MODERATE;
        if (score >= 20) return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }
}
