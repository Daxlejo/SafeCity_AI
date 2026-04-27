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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador de Clasificación IA para reportes.
 *
 * Arquitectura (SOLID):
 * ────────────────────
 * - OpenRouterClient: HTTP + retry + caché (infraestructura)
 * - ReportDecisionEngine: reglas de consenso (lógica de negocio)
 * - IAClassificationService: orquestador (coordina flujo secuencial)
 *
 * Flujo secuencial inteligente:
 * 1. Capa 1 (Gemma): filtro rápido
 * 2. Evaluar confianza: si alta o baja → retornar sin Hermes
 * 3. Capa 2 (Hermes): solo si Gemma está en zona gris
 * 4. Consenso: combinar ambos resultados
 */
@Slf4j
@Service
public class IAClassificationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationUserService notificationUserService;
    private final OpenRouterClient openRouterClient;
    private final ReportDecisionEngine decisionEngine;

    // Puntos de reputación
    private static final double PENALTY_REJECTED = 5.0;  // -5 por reporte rechazado (score 0)
    private static final double BONUS_VERIFIED = 2.0;    // +2 por reporte verificado (score ≥ 60)

    @Value("${app.openrouter.api-key}")
    private String openRouterApiKey;

    @Value("${app.openrouter.model:google/gemma-3-27b-it:free}")
    private String openRouterModel;

    public IAClassificationService(ReportRepository reportRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            NotificationUserService notificationUserService,
            OpenRouterClient openRouterClient,
            ReportDecisionEngine decisionEngine) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationUserService = notificationUserService;
        this.openRouterClient = openRouterClient;
        this.decisionEngine = decisionEngine;
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

            if (result.getTrustScore() >= 60.0 && result.getShouldVerify()) {
                report.setStatus(ReportStatus.VERIFIED);
                log.info("[IA-Async] Reporte {} auto-verificado (score: {}, shouldVerify: true)",
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
                .photoUrl(report.getPhotoUrl())
                .trustScore(report.getTrustScore())
                .aiAnalysis(report.getAiAnalysis())
                .zoneId(report.getZoneId())
                .reportDate(report.getReportDate())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // PIPELINE IA SECUENCIAL (Gemma → evaluar → Hermes si es ambiguo)
    // ═══════════════════════════════════════════════════════════════
    //
    // Flujo:
    // 1. Gemma clasifica primero (Capa 1 — filtro rápido)
    // 2. Si confianza ALTA (>85) o rechazo claro (<20) → retornamos
    // 3. Si confianza AMBIGUA (20-85) → llamamos a Hermes (Capa 2)
    // 4. Motor de consenso decide el veredicto final
    //
    // Ventaja vs paralelo: reduce llamadas API a la mitad en ~70% de casos
    //

    private IAClassificationDTO classifyWithAI(Report report) {
        String prompt = buildPrompt(report);

        // ═══ CAPA 1: Gemma (filtro rápido) ═══
        IAClassificationDTO gemmaResult;
        try {
            gemmaResult = openRouterClient.classify(
                    prompt, OpenRouterClient.GEMMA_MODEL, report.getId(), "Gemma");
            log.info("[Pipeline] Capa 1 (Gemma) reporte #{}: score={}",
                    report.getId(), gemmaResult.getTrustScore());
        } catch (Exception e) {
            log.warn("[Pipeline] Gemma falló para reporte #{}: {}", report.getId(), e.getMessage());
            throw e; // caerá a heurística en classifyReport()
        }

        // ═══ PUNTO DE DECISIÓN: ¿necesitamos a Hermes? ═══
        if (!decisionEngine.needsSecondOpinion(gemmaResult)) {
            // Gemma es suficiente → retornamos sin gastar otra llamada API
            gemmaResult.setReasoning("[Solo Gemma - Confianza clara] " + gemmaResult.getReasoning());
            return gemmaResult;
        }

        // ═══ CAPA 2: Hermes (solo si Gemma duda) ═══
        IAClassificationDTO hermesResult;
        try {
            hermesResult = openRouterClient.classify(
                    prompt, OpenRouterClient.HERMES_MODEL, report.getId(), "Hermes");
            log.info("[Pipeline] Capa 2 (Hermes) reporte #{}: score={}",
                    report.getId(), hermesResult.getTrustScore());
        } catch (Exception e) {
            log.warn("[Pipeline] Hermes falló para reporte #{}: {}. Usando solo Gemma.",
                    report.getId(), e.getMessage());
            gemmaResult.setReasoning("[Solo Gemma - Hermes no disponible] " + gemmaResult.getReasoning());
            return gemmaResult;
        }

        // ═══ CONSENSO: combinar ambos resultados ═══
        return decisionEngine.applyConsensus(gemmaResult, hermesResult, report.getId());
    }

    private String buildPrompt(Report report) {
        StringBuilder p = new StringBuilder();

        // === ROL Y CONTEXTO ===
        p.append("Eres un evaluador ESTRICTO de reportes de seguridad para SafeCityAI en Pasto, Colombia.\n");
        p.append("Tu trabajo es asignar un puntaje de confianza (trustScore 0-100) que refleje ");
        p.append("qué tan ÚTIL y ACCIONABLE es este reporte para las autoridades.\n");
        p.append("Eres EXIGENTE: solo los reportes con información concreta reciben puntajes altos.\n\n");

        // === DATOS DEL REPORTE ===
        p.append("=== REPORTE A EVALUAR ===\n");
        p.append("Descripción: \"").append(report.getDescription()).append("\"\n");
        p.append("Categoría: ").append(report.getIncidentType()).append("\n");
        p.append("GPS: ").append(report.getLatitude() != null ? "SÍ" : "NO").append("\n");
        p.append("Foto: ").append(report.getPhotoUrl() != null ? "SÍ" : "NO").append("\n");
        if (report.getReportedBy() != null) {
            Double avg = reportRepository.findAverageTrustScoreByUser(report.getReportedBy().getId());
            if (avg != null) {
                p.append("Reputación usuario: ").append(String.format("%.0f%%\n", avg));
            }
        }
        p.append("\n");

        // === FASE 1: RECHAZO INMEDIATO ===
        p.append("=== FASE 1: RECHAZO INMEDIATO → trustScore=0 ===\n");
        p.append("Si detectas CUALQUIERA de estos, responde trustScore=0 SIN CALCULAR nada más:\n");
        p.append("• Leyendas/sobrenatural: llorona, mano peluda, fantasmas, duende, diablo\n");
        p.append("• Jerga/burla: jajaja, xd, lol, 🤣, 😂\n");
        p.append("• Armas absurdas: cuchillo de goma, pistola de papel, espada de cartón\n");
        p.append("• Exageraciones: mil muertos, nadie sobrevivió, explosión nuclear\n");
        p.append("• Xenofobia: culpar nacionalidades/etnias de problemas\n");
        p.append("• Servicios públicos: agua, luz, basura, baches\n");
        p.append("• Opinión política o insultos\n");
        p.append("• Texto incoherente o gibberish\n");
        p.append("GPS, foto o reputación NO rescatan un reporte que activa FASE 1.\n\n");

        // === FASE 2: PUNTUACIÓN ESTRICTA ===
        p.append("=== FASE 2: PUNTUACIÓN (solo si pasó FASE 1) ===\n\n");

        p.append("REGLA DE ORO: Un reporte DEBE responder al menos 2 de estas 3 preguntas ");
        p.append("para superar 50 puntos:\n");
        p.append("1. ¿QUÉ pasó exactamente? (acción concreta, no suposición)\n");
        p.append("2. ¿A QUIÉN afectó o QUIÉN lo hizo? (víctima o agresor identificable)\n");
        p.append("3. ¿DÓNDE y CUÁNDO? (lugar específico, hora)\n");
        p.append("Si no responde al menos 2 → MÁXIMO 45 puntos sin importar GPS/foto.\n\n");

        p.append("CÁLCULO:\n");
        p.append("Base: 15 puntos (existe y no es basura)\n");
        p.append("+ Evento concreto que YA OCURRIÓ (no especulación): +20\n");
        p.append("+ Detalles accionables (placa, ropa, dirección exacta, hora): +25\n");
        p.append("+ Víctima o agresor identificable: +10\n");
        p.append("+ GPS verificado: +10 (SOLO si la descripción ya tiene calidad ≥ 40)\n");
        p.append("+ Foto adjunta: +10 (SOLO si la descripción ya tiene calidad ≥ 40)\n");
        p.append("+ Reputación alta del usuario (≥70): +5\n\n");

        p.append("PENALIZACIONES (aplicar SIEMPRE, incluso si suma alta):\n");
        p.append("• Lenguaje especulativo ('parecía que', 'como que', 'creo que vi', ");
        p.append("'querían', 'iban a', 'sospechoso'): TECHO MÁXIMO 35 puntos para todo el reporte.\n");
        p.append("• Sin incidente concreto ('vi algo raro', 'tener cuidado por esta zona'): -30\n");
        p.append("• Demasiado corto o vago (menos de 3 datos útiles): -20\n");
        p.append("• Rumor ('me dijeron', 'escuché que', 'dicen que'): -25\n");
        p.append("• Advertencia genérica sin hechos ('cuidado', 'peligroso', 'zona caliente'): -20\n\n");

        // === EJEMPLOS DE CALIBRACIÓN ===
        p.append("=== EJEMPLOS DE CALIBRACIÓN (usa estos como referencia) ===\n");
        p.append("SCORE 0: 'La llorona me persiguió' → sobrenatural\n");
        p.append("SCORE 0: 'Peleas entre venezolanos' → xenófobo\n");
        p.append("SCORE 20-30: 'Vi 2 sospechosos como que querían robar a alguien' → ");
        p.append("ESPECULATIVO, no pasó nada concreto, sin detalles, techo 35\n");
        p.append("SCORE 25-35: 'Robo con arma blanca, tener cuidado por esta zona' → ");
        p.append("Menciona un evento pero sin detalles útiles (¿cuándo? ¿a quién? ¿descripción del agresor?). ");
        p.append("'Tener cuidado' es advertencia genérica, no información accionable.\n");
        p.append("SCORE 30-40: 'Me robaron el celular en el centro' → ");
        p.append("Evento concreto pero sin detalles (¿hora? ¿cuántos? ¿descripción?)\n");
        p.append("SCORE 45-55: 'Choque entre moto y carro en la Av. Chile, hay heridos' → ");
        p.append("Evento concreto + ubicación + consecuencias visibles\n");
        p.append("SCORE 60-70: 'A las 9pm dos tipos en moto me robaron el celular en la calle 18, ");
        p.append("uno tenía camiseta negra' + GPS → Evento + hora + descripción parcial + GPS\n");
        p.append("SCORE 80-95: 'Robo a mano armada, 2 sujetos en moto negra sin placa, ");
        p.append("asaltaron a una señora en la esquina de la carrera 27 con calle 18' + GPS + foto → ");
        p.append("Evento concreto + víctima + descripción del vehículo + ubicación exacta + evidencia\n\n");

        // === CATEGORÍAS ===
        p.append("=== CATEGORÍAS ===\n");
        p.append("ROBBERY | ACCIDENT | TRAFFIC | TRANSIT_OP | OTHER\n\n");

        // === FORMATO ===
        p.append("Responde SOLO este JSON, sin texto adicional:\n");
        p.append("{\"trustScore\":<0-100>,");
        p.append("\"suggestedType\":\"<TIPO>\",");
        p.append("\"reasoning\":\"<explicación breve>\",");
        p.append("\"shouldVerify\":<true si trustScore>=80>}\n");

        return p.toString();
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

        // Filtro de contenido inválido (leyendas, xenofobia, armas absurdas)
        String invalidReason = detectInvalidContent(desc);
        if (invalidReason != null) {
            log.info("[Heuristica] Contenido inválido detectado: '{}' → {}",
                    desc.substring(0, Math.min(desc.length(), 40)), invalidReason);
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

    /**
     * Detecta contenido inválido que la heurística debe rechazar (score = 0).
     * Es el equivalente de FASE 1 del prompt, pero para cuando la IA no responde.
     * @return razón de rechazo, o null si el contenido es válido.
     */
    private String detectInvalidContent(String text) {
        String lower = text.toLowerCase();

        // Leyendas y seres sobrenaturales
        String[] legends = {
                "mano peluda", "la llorona", "el coco", "el duende", "el diablo",
                "fantasma", "bruja", "aparecido", "espanto", "demonio",
                "chupacabra", "pie grande", "alien", "ovni", "extraterrestre"
        };
        for (String legend : legends) {
            if (lower.contains(legend))
                return "Leyenda/sobrenatural: '" + legend + "'";
        }

        // Armas u objetos absurdos
        String[] absurdWeapons = {
                "cuchillo de goma", "pistola de papel", "espada de cartón",
                "bala de algodón", "arma de juguete", "pistola de agua",
                "cuchillo de plástico", "granada de mentira"
        };
        for (String weapon : absurdWeapons) {
            if (lower.contains(weapon))
                return "Arma/objeto absurdo: '" + weapon + "'";
        }

        // Xenofobia — nacionalidades + contexto de conflicto
        String[] nationalities = {
                "venezolano", "venezolana", "venezolanos", "venezolanas",
                "ecuatoriano", "ecuatoriana", "ecuatorianos", "ecuatorianas",
                "colombiano", "colombiana", "colombianos", "colombianas",
                "peruano", "peruana", "peruanos", "peruanas",
                "inmigrante", "inmigrantes", "extranjero", "extranjeros"
        };
        String[] conflictVerbs = {
                "pelea", "pelean", "peleas", "peleando",
                "entre", "contra", "causan", "culpa", "invaden"
        };
        for (String nat : nationalities) {
            if (lower.contains(nat)) {
                for (String verb : conflictVerbs) {
                    if (lower.contains(verb))
                        return "Contenido xenófobo: menciona '" + nat + "' + '" + verb + "'";
                }
            }
        }

        // Jerga de internet y burlas
        String[] slang = {
                "jajaja", "jeje", "xd", "lol", "lmao", "rofl",
                "me cayó el veinte", "🤣", "😂", "💀"
        };
        for (String s : slang) {
            if (lower.contains(s))
                return "Jerga/burla: '" + s + "'";
        }

        // Contenido político
        String[] political = {
                "gobierno", "presidente", "alcalde", "gobernador",
                "petro", "uribe", "congreso", "senado", "elecciones",
                "partido político", "votación"
        };
        for (String p : political) {
            if (lower.contains(p))
                return "Contenido político: '" + p + "'";
        }
        // Exageraciones absurdas
        String[] exaggerations = {
                "mil personas", "mil muertos", "cien muertos", "miles de personas",
                "todo el barrio", "todos me persiguen", "nadie sobrevivió",
                "explosión nuclear", "bomba atómica", "fin del mundo"
        };
        for (String ex : exaggerations) {
            if (lower.contains(ex))
                return "Exageración absurda: '" + ex + "'";
        }

        // Rumores de segunda mano (sin presencia directa)
        String[] rumors = {
                "me dijeron que", "dicen que", "me contaron que",
                "escuché que", "parece que hubo", "creo que vi"
        };
        for (String r : rumors) {
            if (lower.contains(r))
                return "Rumor de segunda mano: '" + r + "'";
        }

        return null; // contenido válido
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
