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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Orquestador de Clasificación IA — Arquitectura Single-Layer Multimodal.
 *
 * Flujo:
 * 1. Pre-filtro heurístico (detectInvalidContent / isGibberish)
 * 2. Llamada ÚNICA a GPT-4o-mini con texto + imagen (si existe) + trustLevel del usuario
 * 3. La IA responde con JSON estructurado → IAClassificationDTO
 * 4. Se aplica statusDecision directamente (VERIFIED / REJECTED / PENDING)
 */
@Slf4j
@Service
public class IAClassificationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationUserService notificationUserService;
    private final AIClient aiClient;

    private static final double PENALTY_REJECTED = 5.0;
    private static final double BONUS_VERIFIED = 2.0;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public IAClassificationService(ReportRepository reportRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            NotificationUserService notificationUserService,
            AIClient aiClient) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationUserService = notificationUserService;
        this.aiClient = aiClient;
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC: Synchronous classification (used by IAController)
    // ═══════════════════════════════════════════════════════════════

    public IAClassificationDTO classifyReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

        IAClassificationDTO result;
        try {
            result = classifyWithAI(report);
            log.info("[Pipeline] Report #{} classified (model: {}, score: {})",
                    reportId, aiClient.getModelId(), result.getTrustScore());
        } catch (Exception e) {
            log.warn("[Pipeline] AI failed for report #{}. Reason: {}. Using heuristics.",
                    reportId, e.getMessage());
            result = classifyWithHeuristics(report);
            result.setReasoning("[Heuristic Fallback] " + result.getReasoning());
        }

        report.setTrustScore(result.getTrustScore());
        reportRepository.save(report);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // ASYNC: Background classification (called by ReportService)
    // ═══════════════════════════════════════════════════════════════

    @Async("iaExecutor")
    @Transactional
    public void classifyAsync(Long reportId) {
        log.info("[IA-Async] Starting classification for report #{}", reportId);

        try {
            IAClassificationDTO result = classifyReport(reportId);

            Report report = reportRepository.findById(reportId).orElse(null);
            if (report == null) {
                log.warn("[IA-Async] Report #{} not found, possibly deleted", reportId);
                return;
            }

            report.setAiAnalysis(result.getReasoning());

            // Reclassify type if AI suggests different
            IncidentType originalType = report.getIncidentType();
            boolean typeChanged = false;
            if (result.getSuggestedType() != null
                    && !result.getSuggestedType().equals(originalType)) {
                report.setIncidentType(result.getSuggestedType());
                typeChanged = true;
                log.info("[IA-Async] Report #{} reclassified: {} → {}",
                        reportId, originalType, result.getSuggestedType());
            }

            User reportOwner = report.getReportedBy();
            ReportStatus decision = result.getStatusDecision() != null
                    ? result.getStatusDecision() : ReportStatus.PENDING;

            switch (decision) {
                case VERIFIED -> handleVerified(report, reportOwner, result, typeChanged, originalType);
                case REJECTED -> handleRejected(report, reportOwner, result);
                default -> handlePending(report, reportOwner, result, typeChanged, originalType);
            }

        } catch (Exception e) {
            log.error("[IA-Async] Error classifying report #{}: {}", reportId, e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STATUS HANDLERS — WebSocket broadcasts happen AFTER commit
    // ═══════════════════════════════════════════════════════════════

    private void handleVerified(Report report, User owner, IAClassificationDTO result,
            boolean typeChanged, IncidentType originalType) {
        report.setStatus(ReportStatus.VERIFIED);
        reportRepository.save(report);
        log.info("[IA-Async] Report #{} VERIFIED (score: {})", report.getId(), result.getTrustScore());

        if (owner != null) {
            adjustTrustLevel(owner, BONUS_VERIFIED);
            String msg = typeChanged
                    ? "Tu reporte #" + report.getId() + " fue aceptado pero se reclasificó de "
                            + originalType + " a " + result.getSuggestedType()
                            + ". +" + (int) BONUS_VERIFIED + " puntos de reputación."
                    : "Tu reporte #" + report.getId() + " fue verificado exitosamente. +"
                            + (int) BONUS_VERIFIED + " puntos de reputación.";
            String title = typeChanged ? "Reporte reclasificado" : "Reporte verificado ✅";
            String type = typeChanged ? "WARNING" : "INFO";
            notificationUserService.createNotification(owner, report, title, msg, type);
        }

        // Broadcast WebSocket DESPUÉS del commit para evitar notificaciones fantasma
        ReportResponseDTO dto = convertToDTO(report);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.notifyReportUpdated(dto);
            }
        });
    }

    private void handleRejected(Report report, User owner, IAClassificationDTO result) {
        if (owner != null) {
            adjustTrustLevel(owner, -PENALTY_REJECTED);
            String reason = result.getReasoning() != null ? result.getReasoning() : "contenido no válido";
            notificationUserService.createNotification(owner, null,
                    "⚠️ Reporte rechazado",
                    "Tu reporte #" + report.getId() + " fue rechazado: " + reason
                            + ". Perdiste " + (int) PENALTY_REJECTED + " puntos de reputación.",
                    "ALERT");
            log.info("[IA-Async] User {} penalized -{} trustLevel for rejected report #{}",
                    owner.getId(), (int) PENALTY_REJECTED, report.getId());
        }

        log.info("[IA-Async] Report #{} REJECTED (score: {})", report.getId(), result.getTrustScore());
        Long deletedId = report.getId();
        reportRepository.delete(report);

        // Broadcast WebSocket DESPUÉS del commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.notifyReportDeleted(deletedId);
            }
        });
    }

    private void handlePending(Report report, User owner, IAClassificationDTO result,
            boolean typeChanged, IncidentType originalType) {
        report.setStatus(ReportStatus.PENDING);
        reportRepository.save(report);
        log.info("[IA-Async] Report #{} remains PENDING for manual review (score: {})",
                report.getId(), result.getTrustScore());

        if (owner != null) {
            String msg = typeChanged
                    ? "Tu reporte #" + report.getId() + " está en revisión manual. Se reclasificó de "
                            + originalType + " a " + result.getSuggestedType() + "."
                    : "Tu reporte #" + report.getId() + " está pendiente de revisión manual.";
            notificationUserService.createNotification(owner, report,
                    "Reporte en revisión", msg, typeChanged ? "WARNING" : "INFO");
        }

        // Broadcast WebSocket DESPUÉS del commit
        ReportResponseDTO dto = convertToDTO(report);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.notifyReportUpdated(dto);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // SINGLE-LAYER AI CLASSIFICATION (text + image + trustLevel)
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO classifyWithAI(Report report) {
        String systemPrompt = buildSystemPrompt(report);
        List<Map<String, Object>> userContent = buildUserContent(report);

        return aiClient.classifyMultimodal(systemPrompt, userContent, report.getId());
    }

    private String buildSystemPrompt(Report report) {
        double userTrustLevel = 50.0;
        String userRole = "CITIZEN";
        boolean isVerified = false;

        if (report.getReportedBy() != null) {
            User user = report.getReportedBy();
            userTrustLevel = user.getTrustLevel() != null ? user.getTrustLevel() : 50.0;
            userRole = user.getRole() != null ? user.getRole().name() : "CITIZEN";
            isVerified = userTrustLevel >= 70;
        }

        String reputationLine = "";
        if (report.getReportedBy() != null) {
            Double avg = reportRepository.findAverageTrustScoreByUser(report.getReportedBy().getId());
            if (avg != null) reputationLine = "Avg past score: " + String.format("%.0f", avg) + "/100\n";
        }

        String trustPolicy = isVerified
                ? "Reporter is VERIFIED (trust>=70): apply presumption of truthfulness, be more flexible.\n"
                : "Reporter is UNVERIFIED: apply normal strict scoring.\n";

        return """
                Security report evaluator for SafeCityAI, Pasto Colombia.
                Assign trustScore(0-100). Respond ONLY with the JSON below.

                REPORTER: role=%s trust=%.0f %s%s
                REJECT IMMEDIATELY (trustScore=0, statusDecision=REJECTED) if:
                - Supernatural/legends | slang (jajaja/xd/lol) | absurd weapons | extreme exaggerations
                - Xenophobia | political content | public services (water/power/garbage) | gibberish

                SCORING (if not rejected):
                Base 15. +20 concrete past event. +25 actionable details. +10 victim/aggressor. +10 GPS(if quality>=40). +10 photo(if quality>=40). +5 high reputation.
                MAX 45 if report doesn't answer at least 2 of: WHAT/WHO/WHERE+WHEN.
                CEILING 35 if speculative language ('parece','creo','sospechoso','querían').
                PENALTIES: -30 no concrete incident | -20 vague | -25 hearsay | -20 generic warning.

                IMAGE: If provided, analyze jointly. Corroborating image = significant score boost.

                STATUS: score>=60 → VERIFIED | score==0 → REJECTED | else → PENDING
                CATEGORIES: ROBBERY | ACCIDENT | TRAFFIC | TRANSIT_OP | OTHER

                OUTPUT (JSON only, no extra text):
                {"trustScore":<0-100>,"suggestedType":"<CATEGORY>","reasoning":"<1 sentence in Spanish>","statusDecision":"<PENDING|REJECTED|VERIFIED>"}
                """.formatted(userRole, userTrustLevel, trustPolicy, reputationLine);
    }

    private List<Map<String, Object>> buildUserContent(Report report) {
        List<Map<String, Object>> content = new ArrayList<>();

        // Text part — report details
        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append("=== REPORT TO EVALUATE ===\n");
        textBuilder.append("Description: \"").append(report.getDescription()).append("\"\n");
        textBuilder.append("Category: ").append(report.getIncidentType()).append("\n");
        textBuilder.append("GPS: ").append(report.getLatitude() != null ? "YES" : "NO").append("\n");
        textBuilder.append("Photo: ").append(report.getPhotoUrl() != null ? "YES" : "NO").append("\n");

        content.add(Map.of("type", "text", "text", textBuilder.toString()));

        // Image part — encode to Base64 if photo exists on disk
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) {
            String base64Image = encodeImageToBase64(report.getPhotoUrl());
            if (base64Image != null) {
                content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", base64Image, "detail", "low")));
                log.info("[Pipeline] Image attached for report #{}", report.getId());
            }
        }

        return content;
    }

    /**
     * Reads the image file from the upload directory and encodes it as a Base64 data URI.
     * Returns null if the file doesn't exist or can't be read.
     */
    private String encodeImageToBase64(String photoUrl) {
        try {
            // photoUrl is typically "/uploads/filename.jpg" — extract just the filename
            String filename = photoUrl.contains("/")
                    ? photoUrl.substring(photoUrl.lastIndexOf("/") + 1)
                    : photoUrl;

            Path filePath = Paths.get(uploadDir, filename);

            if (!Files.exists(filePath)) {
                log.debug("[Pipeline] Image file not found: {}", filePath);
                return null;
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String base64 = Base64.getEncoder().encodeToString(fileBytes);

            // Detect MIME type from extension
            String mimeType = "image/jpeg";
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) mimeType = "image/png";
            else if (lower.endsWith(".gif")) mimeType = "image/gif";
            else if (lower.endsWith(".webp")) mimeType = "image/webp";

            return "data:" + mimeType + ";base64," + base64;

        } catch (IOException e) {
            log.warn("[Pipeline] Could not encode image to Base64: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TRUST LEVEL ADJUSTMENT
    // ═══════════════════════════════════════════════════════════════

    private void adjustTrustLevel(User user, double delta) {
        double current = user.getTrustLevel() != null ? user.getTrustLevel() : 50.0;
        double newLevel = Math.max(0, Math.min(100, current + delta));
        user.setTrustLevel(newLevel);
        userRepository.save(user);
        log.info("[TrustLevel] User {} adjusted: {} → {} (delta: {})",
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
    // HEURISTIC FALLBACK (when AI is unavailable)
    // ═══════════════════════════════════════════════════════════════

    private IAClassificationDTO classifyWithHeuristics(Report report) {
        double score = calculateTrustScore(report);
        TrustLevel level = scoreToLevel(score);
        IncidentType suggestedType = detectIncidentType(report.getDescription());

        ReportStatus statusDecision;
        if (score == 0.0) statusDecision = ReportStatus.REJECTED;
        else if (score >= 60.0) statusDecision = ReportStatus.VERIFIED;
        else statusDecision = ReportStatus.PENDING;

        return IAClassificationDTO.builder()
                .reportId(report.getId())
                .trustScore(score)
                .trustLevel(level)
                .suggestedType(suggestedType)
                .reasoning(generateReasoning(report, score, suggestedType))
                .statusDecision(statusDecision)
                .shouldVerify(score >= 40.0)
                .build();
    }

    private double calculateTrustScore(Report report) {
        String desc = report.getDescription();

        if (desc == null || desc.isBlank() || isGibberish(desc)) {
            return 0.0;
        }

        String invalidReason = detectInvalidContent(desc);
        if (invalidReason != null) {
            log.info("[Heuristic] Invalid content: '{}' → {}",
                    desc.substring(0, Math.min(desc.length(), 40)), invalidReason);
            return 0.0;
        }

        double score = 50.0;

        if (desc.length() > 100) score += 15;
        else if (desc.length() > 50) score += 10;
        else if (desc.length() < 20) score -= 15;

        if (report.getLatitude() != null && report.getLongitude() != null) score += 15;
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) score += 15;

        if (report.getSource() != null) {
            switch (report.getSource()) {
                case CITIZEN_TEXT, CITIZEN_VOICE -> score += 5;
                case INSTITUTIONAL -> score += 10;
                default -> {}
            }
        }

        if (report.getReportedBy() != null) {
            Double userAvgScore = reportRepository.findAverageTrustScoreByUser(report.getReportedBy().getId());
            if (userAvgScore != null) {
                if (userAvgScore >= 70) score += 10;
                else if (userAvgScore < 30) score -= 10;
            }
        }

        return Math.min(Math.max(score, 0.0), 100.0);
    }

    private boolean isGibberish(String text) {
        if (text == null || text.isBlank()) return true;

        String clean = text.toLowerCase().replaceAll("[^a-záéíóúñü\\s]", "").trim();
        if (clean.length() < 5) return true;

        long vowels = clean.chars().filter(c -> "aeiouáéíóú".indexOf(c) >= 0).count();
        long letters = clean.chars().filter(Character::isLetter).count();
        if (letters > 0) {
            double ratio = (double) vowels / letters;
            if (ratio < 0.15 || ratio > 0.70) return true;
        }

        String[] realWords = {
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
        boolean hasRealWord = false;
        for (String word : realWords) {
            if (lowerText.contains(word)) { hasRealWord = true; break; }
        }

        if (!hasRealWord && clean.length() < 30) return true;
        if (clean.replaceAll("(.)\\1{3,}", "").length() < clean.length() / 2) return true;

        return false;
    }

    private String detectInvalidContent(String text) {
        String lower = text.toLowerCase();

        String[] legends = {
                "mano peluda", "la llorona", "el coco", "el duende", "el diablo",
                "fantasma", "bruja", "aparecido", "espanto", "demonio",
                "chupacabra", "pie grande", "alien", "ovni", "extraterrestre"
        };
        for (String legend : legends) {
            if (lower.contains(legend)) return "Supernatural: '" + legend + "'";
        }

        String[] absurdWeapons = {
                "cuchillo de goma", "pistola de papel", "espada de cartón",
                "bala de algodón", "arma de juguete", "pistola de agua",
                "cuchillo de plástico", "granada de mentira"
        };
        for (String weapon : absurdWeapons) {
            if (lower.contains(weapon)) return "Absurd weapon: '" + weapon + "'";
        }

        String[] nationalities = {
                "venezolano", "venezolana", "venezolanos", "venezolanas",
                "ecuatoriano", "ecuatoriana", "inmigrante", "inmigrantes",
                "extranjero", "extranjeros"
        };
        String[] conflictVerbs = { "pelea", "pelean", "peleando", "entre", "contra", "causan", "culpa", "invaden" };
        for (String nat : nationalities) {
            if (lower.contains(nat)) {
                for (String verb : conflictVerbs) {
                    if (lower.contains(verb)) return "Xenophobic content: '" + nat + "' + '" + verb + "'";
                }
            }
        }

        String[] slang = { "jajaja", "jeje", "xd", "lol", "lmao", "rofl", "🤣", "😂", "💀" };
        for (String s : slang) {
            if (lower.contains(s)) return "Slang/mockery: '" + s + "'";
        }

        String[] political = {
                "gobierno", "presidente", "alcalde", "gobernador",
                "petro", "uribe", "congreso", "senado", "elecciones",
                "partido político", "votación"
        };
        for (String p : political) {
            if (lower.contains(p)) return "Political content: '" + p + "'";
        }

        String[] exaggerations = {
                "mil personas", "mil muertos", "cien muertos", "miles de personas",
                "todo el barrio", "todos me persiguen", "nadie sobrevivió",
                "explosión nuclear", "bomba atómica", "fin del mundo"
        };
        for (String ex : exaggerations) {
            if (lower.contains(ex)) return "Absurd exaggeration: '" + ex + "'";
        }

        String[] rumors = {
                "me dijeron que", "dicen que", "me contaron que",
                "escuché que", "parece que hubo", "creo que vi"
        };
        for (String r : rumors) {
            if (lower.contains(r)) return "Hearsay: '" + r + "'";
        }

        return null;
    }

    private IncidentType detectIncidentType(String description) {
        if (description == null) return IncidentType.OTHER;
        String lower = description.toLowerCase();
        if (lower.contains("robo") || lower.contains("atraco") || lower.contains("hurto")) return IncidentType.ROBBERY;
        if (lower.contains("accidente") || lower.contains("choque")) return IncidentType.ACCIDENT;
        if (lower.contains("trafico") || lower.contains("embotellamiento")) return IncidentType.TRAFFIC;
        if (lower.contains("transporte") || lower.contains("bus") || lower.contains("ruta")) return IncidentType.TRANSIT_OP;
        return IncidentType.OTHER;
    }

    private TrustLevel scoreToLevel(double score) {
        if (score >= 80) return TrustLevel.VERIFIED;
        if (score >= 60) return TrustLevel.HIGH;
        if (score >= 40) return TrustLevel.MODERATE;
        if (score >= 20) return TrustLevel.LOW;
        return TrustLevel.UNTRUSTED;
    }

    private String generateReasoning(Report report, double score, IncidentType suggested) {
        StringBuilder reason = new StringBuilder();
        reason.append("Score: ").append(String.format("%.1f", score)).append("/100. ");
        if (report.getLatitude() != null) reason.append("GPS provided. ");
        if (report.getDescription() != null && report.getDescription().length() > 50) reason.append("Detailed description. ");
        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isBlank()) reason.append("Photo attached. ");
        if (!suggested.equals(report.getIncidentType())) {
            reason.append("Suggested type differs (").append(suggested).append("). ");
        }
        return reason.toString();
    }
}
