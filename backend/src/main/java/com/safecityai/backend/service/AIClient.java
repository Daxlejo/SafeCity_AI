package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;

import java.util.List;
import java.util.Map;

/**
 * Contrato polimórfico para clientes de IA.
 *
 * Implementaciones posibles:
 * - OpenAIClient (API directa de OpenAI — GPT-4o-mini)
 * - Futuras: AnthropicClient, OpenRouterClient, LocalLlamaClient, etc.
 *
 * Principio: OCP (Open/Closed) — se pueden añadir nuevos proveedores
 * sin modificar los servicios que consumen esta interfaz.
 */
public interface AIClient {

    /**
     * Classifies a report using multimodal input (text + optional image).
     *
     * @param systemPrompt   Structured system prompt with context and rules
     * @param userContent    List of content parts (text and/or image_url objects)
     * @param reportId       Report ID for logging and traceability
     * @return Parsed classification result
     */
    IAClassificationDTO classifyMultimodal(
            String systemPrompt,
            List<Map<String, Object>> userContent,
            Long reportId);

    /**
     * Sends a raw text prompt and returns the AI's text response.
     * Used by services that need free-form AI responses (e.g., OSINT entity extraction).
     *
     * @param prompt Full prompt text
     * @param label  Readable label for logging (e.g., "OSINT-AI")
     * @return Raw text content from the AI response
     */
    String sendRawPrompt(String prompt, String label);

    /**
     * Returns the configured model identifier for logging and diagnostics.
     */
    String getModelId();
}
