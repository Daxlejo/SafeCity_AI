package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintAIResultDTO;
import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.OsintNewsArticleRepository;
import com.safecityai.backend.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OsintService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ReportRepository reportRepository;
    private final OsintNewsArticleRepository newsArticleRepository;
    private final GeocodingService geocodingService;
    private final AIClient aiClient;
    private final NotificationService notificationService;
    private final OsintConfigService osintConfigService;

    @Value("${app.rapidapi.key}")
    private String rapidApiKey;

    @Value("${app.rapidapi.facebook-host}")
    private String facebookHost;

    @Value("${app.osint.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.osint.default-city:Pasto}")
    private String defaultCity;

    @Value("${app.osint.max-items-per-execution:10}")
    private int maxItemsPerExecution;

    @Value("${app.osint.dedup-radius-meters:500}")
    private double dedupRadiusMeters;

    @Value("${app.osint.dedup-hours:12}")
    private int dedupHours;

    // Facebook pages — local Pasto sources
    private static final List<String> PRIORITY_FB_PAGES = List.of(
            "Pasto Denuncias",
            "Nariño Noticias La Original",
            "La Voz Del pueblo Noticias Nariño");

    public OsintService(ReportRepository reportRepository,
                        OsintNewsArticleRepository newsArticleRepository,
                        GeocodingService geocodingService,
                        AIClient aiClient,
                        NotificationService notificationService,
                        OsintConfigService osintConfigService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.reportRepository = reportRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.geocodingService = geocodingService;
        this.aiClient = aiClient;
        this.notificationService = notificationService;
        this.osintConfigService = osintConfigService;
    }

    // ═══════════════════════════════════════════════════════════
    // SCHEDULER: Automatic scan — lee config dinámica de la BD
    // ═══════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 3600000, initialDelay = 60000)
    public void scheduledScan() {
        // Prioridad: la config de BD sobreescribe la property estática
        var dbConfig = osintConfigService.getActiveConfig();
        boolean isEnabled = dbConfig.isEnabled() && schedulerEnabled;

        if (!isEnabled) {
            log.debug("[OSINT] Scheduler disabled (DB: {}, property: {}), skipping scan",
                    dbConfig.isEnabled(), schedulerEnabled);
            return;
        }

        // Usar ciudad de la config de BD si está disponible
        String city = dbConfig.getDefaultCity() != null ? dbConfig.getDefaultCity() : defaultCity;
        log.info("[OSINT] Executing scheduled scan for '{}' (interval: {}min)",
                city, dbConfig.getIntervalMinutes());
        try {
            Map<String, Object> result = scanAndClassify(city);
            log.info("[OSINT] Scheduled scan completed: {}", result);
        } catch (Exception e) {
            log.error("[OSINT] Error in scheduled scan: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PIPELINE V2: Scrape → AI Extraction → Geocode → Dedup → Save
    // ═══════════════════════════════════════════════════════════

    public Map<String, Object> scanAndClassify(String city) {
        List<OsintResultDTO> rawResults = searchIncidents(city);
        log.info("[OSINT] scan: {} raw results found for '{}'", rawResults.size(), city);

        // Rate limiting: process max N items per execution
        List<OsintResultDTO> toProcess = rawResults.stream()
                .filter(r -> r.getPublishedAt() == null
                        || !r.getPublishedAt().isBefore(LocalDateTime.now().minusDays(7)))
                .limit(maxItemsPerExecution)
                .toList();

        int created = 0;
        int savedAsNews = 0;
        int duplicatesSkipped = 0;
        int discardedByAI = 0;
        List<Long> reportIds = new ArrayList<>();

        for (OsintResultDTO raw : toProcess) {
            try {
                // Step 1: AI entity extraction + classification (single call)
                OsintAIResultDTO aiResult = extractEntitiesWithAI(raw.getContent());
                if (aiResult == null || !aiResult.isSecurityIncident()) {
                    discardedByAI++;
                    log.debug("[OSINT] Discarded by AI (not security): '{}'",
                            truncate(raw.getContent(), 60));
                    continue;
                }

                // Step 2: Geocode the AI-extracted address
                double[] coords = geocodeAddress(aiResult.getExactAddress());

                if (coords == null) {
                    // Cannot geolocate → save as news article ("Noticias Pasto")
                    saveAsNewsArticle(raw, aiResult);
                    savedAsNews++;
                    continue;
                }

                // Step 3: Geospatial + temporal deduplication
                if (isDuplicateIncident(aiResult.getIncidentType(), coords[0], coords[1])) {
                    duplicatesSkipped++;
                    log.debug("[OSINT] Duplicate detected for {} at [{}, {}]",
                            aiResult.getIncidentType(), coords[0], coords[1]);
                    continue;
                }

                // Step 4: Create Report with AI-enriched data
                Report report = buildReportFromAI(raw, aiResult, coords);
                Report saved = reportRepository.save(report);
                created++;
                reportIds.add(saved.getId());

                // Step 5: Broadcast new report via WebSocket
                notificationService.notifyNewReport(convertToDTO(saved));
                log.info("[OSINT] Report #{} created: {} at [{}, {}] (score: {})",
                        saved.getId(), aiResult.getIncidentType(),
                        coords[0], coords[1], aiResult.getTrustScore());

            } catch (Exception e) {
                log.warn("[OSINT] Error processing item: {}", e.getMessage());
            }
        }

        log.info("[OSINT] Pipeline V2: {} created, {} news, {} duplicates, {} discarded from {} processed",
                created, savedAsNews, duplicatesSkipped, discardedByAI, toProcess.size());

        return Map.of(
                "city", city,
                "found", rawResults.size(),
                "processed", toProcess.size(),
                "reportsCreated", created,
                "savedAsNews", savedAsNews,
                "duplicatesSkipped", duplicatesSkipped,
                "discardedByAI", discardedByAI,
                "reportIds", reportIds);
    }

    // ═══════════════════════════════════════════════════════════
    // AI ENTITY EXTRACTION: Raw text → structured JSON (single call)
    // ═══════════════════════════════════════════════════════════

    private OsintAIResultDTO extractEntitiesWithAI(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        try {
            String prompt = buildOsintExtractionPrompt(rawText);
            String aiResponse = aiClient.sendRawPrompt(prompt, "OSINT-AI");
            return parseAIExtractionResponse(aiResponse);
        } catch (Exception e) {
            log.warn("[OSINT-AI] Extraction failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildOsintExtractionPrompt(String rawText) {
        return """
                You are an OSINT analyst for SafeCityAI in Pasto, Colombia.
                Analyze the following scraped news/social media text and extract structured security information.
                
                === TEXT TO ANALYZE ===
                "%s"
                
                === INSTRUCTIONS ===
                1. Determine if this text describes a REAL security incident (robbery, accident, assault, fire, etc.)
                2. REJECT: political news, cultural events, sports, employment, opinions, rumors, jokes
                3. Extract the EXACT physical address or location mentioned (street, neighborhood, landmark)
                4. Write a clean 1-2 sentence summary of the incident
                5. Estimate when it happened (ISO 8601 format)
                6. Assign a trustScore (0-100) based on specificity and credibility
                
                === CATEGORIES ===
                ROBBERY | ACCIDENT | TRAFFIC | TRANSIT_OP | OTHER
                
                Respond ONLY with this JSON, no additional text:
                {"isSecurityIncident":<true/false>,"incidentType":"<TYPE>","exactAddress":"<address or empty string>","cleanSummary":"<summary>","estimatedDate":"<ISO 8601 or empty>","trustScore":<0-100>,"shouldVerify":<true if score>=60>}
                """.formatted(rawText);
    }

    private OsintAIResultDTO parseAIExtractionResponse(String aiResponse) {
        try {
            JsonNode json = objectMapper.readTree(aiResponse);

            boolean isIncident = json.path("isSecurityIncident").asBoolean(false);

            IncidentType incidentType;
            try {
                incidentType = IncidentType.valueOf(json.path("incidentType").asText("OTHER"));
            } catch (IllegalArgumentException e) {
                incidentType = IncidentType.OTHER;
            }

            return OsintAIResultDTO.builder()
                    .securityIncident(isIncident)
                    .incidentType(incidentType)
                    .exactAddress(json.path("exactAddress").asText(""))
                    .cleanSummary(json.path("cleanSummary").asText(""))
                    .estimatedDate(json.path("estimatedDate").asText(""))
                    .trustScore(json.path("trustScore").asDouble(50.0))
                    .shouldVerify(json.path("shouldVerify").asBoolean(false))
                    .build();
        } catch (Exception e) {
            log.warn("[OSINT-AI] Failed to parse AI response: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // GEOCODING: Address text → real coordinates
    // ═══════════════════════════════════════════════════════════

    private double[] geocodeAddress(String address) {
        if (address == null || address.isBlank()) return null;

        // Append ", Pasto, Colombia" for better geocoding accuracy
        String fullAddress = address.contains("Pasto") ? address : address + ", Pasto, Colombia";
        double[] coords = geocodingService.geocode(fullAddress);

        if (coords != null) {
            log.info("[OSINT-Geocoding] '{}' → [{}, {}]", address, coords[0], coords[1]);
        } else {
            log.info("[OSINT-Geocoding] Could not geolocate: '{}'", address);
        }
        return coords;
    }

    // ═══════════════════════════════════════════════════════════
    // DEDUPLICATION: Geospatial + temporal via bounding box
    // ═══════════════════════════════════════════════════════════

    private boolean isDuplicateIncident(IncidentType type, double lat, double lng) {
        // Convert radius in meters to approximate degrees
        // At equator: 1° ≈ 111,320m. At Pasto latitude (~1.2°N): negligible cos correction
        double deltaLat = dedupRadiusMeters / 111320.0;
        double deltaLng = dedupRadiusMeters / (111320.0 * Math.cos(Math.toRadians(lat)));

        LocalDateTime since = LocalDateTime.now().minusHours(dedupHours);

        return reportRepository.existsNearbyDuplicate(
                type,
                lat - deltaLat, lat + deltaLat,
                lng - deltaLng, lng + deltaLng,
                since);
    }

    // ═══════════════════════════════════════════════════════════
    // NEWS ARTICLE: Save non-geolocatable incidents
    // ═══════════════════════════════════════════════════════════

    private void saveAsNewsArticle(OsintResultDTO raw, OsintAIResultDTO aiResult) {
        String contentHash = hashContent(aiResult.getCleanSummary());

        // Dedup by content hash for news articles
        if (newsArticleRepository.existsByContentHash(contentHash)) {
            log.debug("[OSINT] News article already exists (hash: {})", contentHash);
            return;
        }

        OsintNewsArticle article = OsintNewsArticle.builder()
                .title(truncate(aiResult.getCleanSummary(), 300))
                .summary(aiResult.getCleanSummary())
                .incidentType(aiResult.getIncidentType())
                .sourceUrl(raw.getSourceUrl())
                .sourceType(raw.getSourceType() != null ? raw.getSourceType() : ReportSource.SOCIAL_MEDIA)
                .contentHash(contentHash)
                .trustScore(aiResult.getTrustScore())
                .estimatedDate(parseEstimatedDate(aiResult.getEstimatedDate()))
                .build();

        newsArticleRepository.save(article);
        log.info("[OSINT] Saved as news article: '{}'", truncate(aiResult.getCleanSummary(), 60));
    }

    // ═══════════════════════════════════════════════════════════
    // REPORT BUILDER: Constructs Report entity from AI data
    // ═══════════════════════════════════════════════════════════

    private Report buildReportFromAI(OsintResultDTO raw, OsintAIResultDTO aiResult, double[] coords) {
        ReportStatus status = aiResult.getTrustScore() >= 60 && aiResult.isShouldVerify()
                ? ReportStatus.VERIFIED
                : ReportStatus.PENDING;

        return Report.builder()
                .description(aiResult.getCleanSummary())
                .incidentType(aiResult.getIncidentType())
                .address(aiResult.getExactAddress())
                .latitude(coords[0])
                .longitude(coords[1])
                .source(raw.getSourceType() != null ? raw.getSourceType() : ReportSource.SOCIAL_MEDIA)
                .status(status)
                .trustScore(aiResult.getTrustScore())
                .aiAnalysis("[OSINT V2] " + aiResult.getCleanSummary())
                .reportDate(LocalDateTime.now())
                .incidentDate(parseEstimatedDate(aiResult.getEstimatedDate()))
                .build();
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
                .incidentDate(report.getIncidentDate())
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH: Aggregate all sources (preview, no side effects)
    // ═══════════════════════════════════════════════════════════

    public List<OsintResultDTO> searchIncidents(String city) {
        List<OsintResultDTO> results = new ArrayList<>();

        // Source 1 (PRIORITY): Local Pasto Facebook pages
        try {
            List<OsintResultDTO> priorityResults = searchPriorityPages();
            results.addAll(priorityResults);
            log.info("FB Priority: {} results", priorityResults.size());
        } catch (Exception e) {
            log.warn("Error in FB priority pages: {}", e.getMessage());
        }

        // Source 2: Google News RSS (free, unlimited)
        try {
            List<OsintResultDTO> newsResults = searchGoogleNews(city);
            results.addAll(newsResults);
            log.info("Google News: {} results for '{}'", newsResults.size(), city);
        } catch (Exception e) {
            log.warn("Error in Google News: {}", e.getMessage());
        }

        // Source 3: Facebook general search
        try {
            List<OsintResultDTO> fbResults = searchFacebook(city);
            results.addAll(fbResults);
            log.info("Facebook general: {} results for '{}'", fbResults.size(), city);
        } catch (Exception e) {
            log.warn("Error in Facebook Scraper: {}", e.getMessage());
        }

        log.info("OSINT total: {} combined results", results.size());
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    // SOURCE 1 (PRIORITY): Local Pasto FB pages
    // ═══════════════════════════════════════════════════════════

    private List<OsintResultDTO> searchPriorityPages() {
        List<OsintResultDTO> results = new ArrayList<>();

        for (String pageName : PRIORITY_FB_PAGES) {
            try {
                String url = String.format(
                        "https://%s/search/pages?query=%s",
                        facebookHost, pageName.replace(" ", "+"));

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-rapidapi-key", rapidApiKey);
                headers.set("x-rapidapi-host", facebookHost);

                HttpEntity<String> request = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, request, String.class);

                String body = response.getBody();
                if (body == null) continue;

                JsonNode root = objectMapper.readTree(body);
                JsonNode items = root.isArray() ? root : root.path("results");

                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String name = item.has("name") ? item.get("name").asText() : "";
                        String description = item.has("description")
                                ? item.get("description").asText() : "";
                        String pageUrl = item.has("url") ? item.get("url").asText()
                                : item.has("link") ? item.get("link").asText() : "";

                        String content = !description.isBlank() ? description : name;

                        LocalDateTime pubDate = parseFacebookDate(item);

                        if (!content.isBlank()) {
                            results.add(OsintResultDTO.builder()
                                    .title("[FB Priority] " + name)
                                    .content(content)
                                    .sourceUrl(pageUrl)
                                    .sourceType(ReportSource.SOCIAL_MEDIA)
                                    .detectedLocation("Pasto")
                                    .publishedAt(pubDate)
                                    .confidence(0.80)
                                    .build());
                        }
                    }
                }

                log.info("FB Priority '{}': OK", pageName);
            } catch (Exception e) {
                log.warn("Error scraping '{}': {}", pageName, e.getMessage());
            }
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════
    // SOURCE 2: Google News RSS (free, unlimited)
    // ═══════════════════════════════════════════════════════════

    private List<OsintResultDTO> searchGoogleNews(String city) {
        List<OsintResultDTO> results = new ArrayList<>();

        // Session 1: general incidents
        String query1 = "(accidente+OR+robo+OR+atraco+OR+hurto+OR+homicidio+OR+choque)+"
                + city.replace(" ", "+");
        results.addAll(fetchGoogleNewsRSS(query1, city, 8));

        // Session 2: geolocated incidents with Pasto landmarks
        String query2 = "(accidente+OR+robo+OR+choque+OR+herido+OR+atropello)"
                + "+(panamericana+OR+lorenzo+OR+anganoy+OR+avenida+OR+sector+OR+barrio)+"
                + city.replace(" ", "+");
        results.addAll(fetchGoogleNewsRSS(query2, city, 8));

        log.info("Google News total (both sessions): {} results for '{}'", results.size(), city);
        return results;
    }

    private List<OsintResultDTO> fetchGoogleNewsRSS(String query, String city, int maxItems) {
        List<OsintResultDTO> results = new ArrayList<>();
        String url = String.format(
                "https://news.google.com/rss/search?q=%s&hl=es-419&gl=CO&ceid=CO:es-419",
                query);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String xml = response.getBody();
            if (xml == null) return results;

            Pattern itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
            Matcher itemMatcher = itemPattern.matcher(xml);

            int count = 0;
            while (itemMatcher.find() && count < maxItems) {
                String item = itemMatcher.group(1);
                String title = extractXmlTag(item, "title");
                String link = extractXmlTag(item, "link");
                String pubDateStr = extractXmlTag(item, "pubDate");

                LocalDateTime pubDate = LocalDateTime.now();
                try {
                    if (!pubDateStr.isBlank()) {
                        pubDate = ZonedDateTime.parse(pubDateStr, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toLocalDateTime();
                    }
                } catch (Exception e) { /* ignore malformed date */ }

                // All results pass through — AI does the filtering now
                if (!title.isBlank()) {
                    results.add(OsintResultDTO.builder()
                            .title(title)
                            .content(title)
                            .sourceUrl(link)
                            .sourceType(ReportSource.INSTITUTIONAL)
                            .detectedLocation(city)
                            .publishedAt(pubDate)
                            .confidence(0.70)
                            .build());
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Error in Google News RSS (query={}): {}", query, e.getMessage());
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    // SOURCE 3: Facebook general search via RapidAPI
    // ═══════════════════════════════════════════════════════════

    private List<OsintResultDTO> searchFacebook(String city) {
        List<OsintResultDTO> results = new ArrayList<>();

        String query = city.replace(" ", "+") + "+(robo|atraco|accidente|hurto|choque|homicidio)";
        String url = String.format(
                "https://%s/search/posts?query=%s&count=10",
                facebookHost, query);

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-rapidapi-key", rapidApiKey);
        headers.set("x-rapidapi-host", facebookHost);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            String body = response.getBody();
            if (body == null) return results;

            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.isArray() ? root : root.path("results");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    String text = item.has("text") ? item.get("text").asText()
                            : item.has("message") ? item.get("message").asText()
                            : item.has("name") ? item.get("name").asText() : "";

                    String postUrl = item.has("url") ? item.get("url").asText()
                            : item.has("link") ? item.get("link").asText() : "";

                    LocalDateTime pubDate = parseFacebookDate(item);

                    if (!text.isBlank()) {
                        results.add(OsintResultDTO.builder()
                                .title(truncate(text, 100))
                                .content(text)
                                .sourceUrl(postUrl)
                                .sourceType(ReportSource.SOCIAL_MEDIA)
                                .detectedLocation(city)
                                .publishedAt(pubDate)
                                .confidence(0.50)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Facebook scraper error: {}", e.getMessage());
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private String extractXmlTag(String xml, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("<![CDATA[", "")
                    .replace("]]>", "")
                    .trim();
        }
        return "";
    }

    private LocalDateTime parseFacebookDate(JsonNode item) {
        try {
            if (item.has("created_time")) {
                return ZonedDateTime.parse(
                                item.get("created_time").asText(),
                                DateTimeFormatter.ISO_DATE_TIME)
                        .toLocalDateTime();
            } else if (item.has("time")) {
                return ZonedDateTime.parse(
                                item.get("time").asText(),
                                DateTimeFormatter.ISO_DATE_TIME)
                        .toLocalDateTime();
            }
        } catch (Exception e) {
            // Fallback to current time
        }
        return LocalDateTime.now();
    }

    private LocalDateTime parseEstimatedDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return null;
        try {
            return LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * SHA-256 hash for news article content deduplication.
     */
    private String hashContent(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            String normalized = content.toLowerCase().trim().replaceAll("\\s+", " ");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, digest));
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
