package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.TrustLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Motor de Decisión para clasificación de reportes.
 *
 * Responsabilidades (SRP — Single Responsibility):
 * ─────────────────────────────────────────────────
 * 1. Determinar si Gemma es suficiente o se necesita Hermes
 * 2. Aplicar reglas de consenso cuando ambos modelos participan
 * 3. Resolver discrepancias entre modelos
 *
 * Esta clase NO hace HTTP ni conoce OpenRouter.
 * Solo contiene LÓGICA DE NEGOCIO pura.
 *
 * Patrón: Strategy — las reglas de decisión están centralizadas
 * y pueden intercambiarse o testearse independientemente.
 */
@Slf4j
@Component
public class ReportDecisionEngine {

    // ═══ Umbrales de confianza para el flujo secuencial ═══

    /** Si Gemma da >= HIGH_CONFIDENCE, aceptamos sin consultar Hermes */
    private static final double HIGH_CONFIDENCE_THRESHOLD = 85.0;

    /** Si Gemma da <= LOW_CONFIDENCE, rechazamos sin consultar Hermes */
    private static final double LOW_CONFIDENCE_THRESHOLD = 20.0;

    /** Diferencia máxima aceptable entre ambos modelos */
    private static final double DISCREPANCY_THRESHOLD = 30.0;

    // ═══════════════════════════════════════════════════════════════
    //  ¿Se necesita un segundo modelo?
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evalúa si el resultado de Gemma es suficientemente claro
     * o si necesitamos una segunda opinión de Hermes.
     *
     * Lógica:
     * - Score >= 85 → Gemma está MUY segura → no necesitamos a Hermes
     * - Score <= 20 → Gemma dice que es basura clara → no necesitamos a Hermes
     * - Score entre 21 y 84 → zona gris → SÍ necesitamos a Hermes
     *
     * @param gemmaResult Resultado de la clasificación de Gemma
     * @return true si necesitamos llamar a Hermes como segunda capa
     */
    public boolean needsSecondOpinion(IAClassificationDTO gemmaResult) {
        double score = gemmaResult.getTrustScore();
        boolean needed = score > LOW_CONFIDENCE_THRESHOLD && score <= HIGH_CONFIDENCE_THRESHOLD;

        if (needed) {
            log.info("[Decisión] Gemma score={} está en zona gris [{}-{}]. Llamando a Hermes.",
                    score, LOW_CONFIDENCE_THRESHOLD, HIGH_CONFIDENCE_THRESHOLD);
        } else {
            String reason = score >= HIGH_CONFIDENCE_THRESHOLD ? "ALTA confianza" : "rechazo claro";
            log.info("[Decisión] Gemma score={} → {} → Hermes NO es necesario.", score, reason);
        }

        return needed;
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONSENSO — Combinar resultados de Gemma + Hermes
    // ═══════════════════════════════════════════════════════════════

    /**
     * Motor de Consenso: 3 reglas para decidir el score final
     * cuando AMBOS modelos han respondido.
     *
     * Regla 1: Si ambos rechazan (ambos < 20) → RECHAZADO
     * Regla 2: Discrepancia > 30 puntos → REVISIÓN HUMANA (PENDING)
     * Regla 3: Consenso → score = min(Gemma, Hermes) (conservador)
     *
     * @param gemma    Resultado de Gemma (Capa 1)
     * @param hermes   Resultado de Hermes (Capa 2)
     * @param reportId ID del reporte para logging
     * @return DTO con el veredicto final del consenso
     */
    public IAClassificationDTO applyConsensus(
            IAClassificationDTO gemma, IAClassificationDTO hermes, Long reportId) {

        double gScore = gemma.getTrustScore();
        double hScore = hermes.getTrustScore();
        double diff = Math.abs(gScore - hScore);

        // ═══ REGLA 1: Ambos rechazan → rechazado definitivo ═══
        if (gScore <= LOW_CONFIDENCE_THRESHOLD && hScore <= LOW_CONFIDENCE_THRESHOLD) {
            log.info("[Consenso] Reporte #{} → AMBOS RECHAZAN (G={}, H={}). Score=0.", reportId, gScore, hScore);
            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(0.0)
                    .trustLevel(TrustLevel.UNTRUSTED)
                    .suggestedType(gemma.getSuggestedType())
                    .reasoning("[Consenso: Doble Rechazo] Gemma: " + gemma.getReasoning()
                            + " | Hermes: " + hermes.getReasoning())
                    .shouldVerify(false)
                    .build();
        }

        // ═══ REGLA 2: Discrepancia → revisión humana ═══
        if (diff > DISCREPANCY_THRESHOLD) {
            double finalScore = Math.min(gScore, hScore);
            log.info("[Consenso] Reporte #{} → DISCREPANCIA (G={}, H={}, diff={}). → PENDING",
                    reportId, gScore, hScore, diff);

            IncidentType chosenType = gScore >= hScore
                    ? gemma.getSuggestedType() : hermes.getSuggestedType();

            return IAClassificationDTO.builder()
                    .reportId(reportId)
                    .trustScore(finalScore)
                    .trustLevel(scoreToLevel(finalScore))
                    .suggestedType(chosenType)
                    .reasoning(String.format(
                            "[Consenso: Discrepancia %.0f pts] Gemma (%.0f): %s | Hermes (%.0f): %s",
                            diff, gScore, gemma.getReasoning(), hScore, hermes.getReasoning()))
                    .shouldVerify(false) // queda PENDING, NO auto-verificar
                    .build();
        }

        // ═══ REGLA 3: Consenso → score = min(G, H) (conservador) ═══
        double finalScore = Math.min(gScore, hScore);
        IncidentType chosenType = gScore <= hScore
                ? gemma.getSuggestedType() : hermes.getSuggestedType();

        log.info("[Consenso] Reporte #{} → ACUERDO (G={}, H={}, final={})", reportId, gScore, hScore, finalScore);

        return IAClassificationDTO.builder()
                .reportId(reportId)
                .trustScore(finalScore)
                .trustLevel(scoreToLevel(finalScore))
                .suggestedType(chosenType)
                .reasoning(String.format(
                        "[Consenso IA Dual] Gemma (%.0f): %s | Hermes (%.0f): %s",
                        gScore, gemma.getReasoning(), hScore, hermes.getReasoning()))
                .shouldVerify(finalScore >= 70.0)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═══════════════════════════════════════════════════════════════

    /** Convierte un score numérico al enum TrustLevel. */
    static TrustLevel scoreToLevel(double score) {
        if (score >= 80) return TrustLevel.VERIFIED;
        if (score >= 60) return TrustLevel.HIGH;
        if (score >= 30) return TrustLevel.MODERATE;
        if (score >= 20) return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }
}
