package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
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
        private final IAClassificationService iaService;

        @Value("${app.rapidapi.key}")
        private String rapidApiKey;

        @Value("${app.rapidapi.facebook-host}")
        private String facebookHost;

        public OsintService(ReportRepository reportRepository,
                        IAClassificationService iaService) {
                this.restTemplate = new RestTemplate();
                this.objectMapper = new ObjectMapper();
                this.reportRepository = reportRepository;
                this.iaService = iaService;
        }

        // Paginas de Facebook prioritarias de Pasto (fuentes confiables locales)
        private static final List<String> PRIORITY_FB_PAGES = List.of(
                        "Pasto Denuncias",
                        "Nariño Noticias La Original",
                        "La Voz Del pueblo Noticias Nariño");

        public Map<String, Object> scanAndClassify(String city) {
                // Paso 1: Buscar en todas las fuentes
                List<OsintResultDTO> osintResults = searchIncidents(city);
                log.info("OSINT scan: {} resultados encontrados para '{}'", osintResults.size(), city);

                int created = 0;
                int classified = 0;
                List<Long> reportIds = new ArrayList<>();

                // Paso 2 y 3: Por cada resultado, crear reporte y clasificar
                for (OsintResultDTO osint : osintResults) {
                        try {
                                // Crear el reporte en la BD
                                Report report = Report.builder()
                                                .description(osint.getContent())
                                                .incidentType(IncidentType.OTHER)
                                                .address(osint.getDetectedLocation())
                                                .latitude(osint.getLatitude())
                                                .longitude(osint.getLongitude())
                                                .source(osint.getSourceType() != null
                                                                ? osint.getSourceType()
                                                                : ReportSource.SOCIAL_MEDIA)
                                                .status(ReportStatus.PENDING)
                                                .reportDate(LocalDateTime.now())
                                                .build();

                                Report saved = reportRepository.save(report);
                                created++;
                                reportIds.add(saved.getId());

                                // Clasificar con Gemini AI
                                try {
                                        iaService.classifyReport(saved.getId());
                                        classified++;
                                        log.info("Reporte OSINT #{} clasificado por IA", saved.getId());
                                } catch (Exception e) {
                                        log.warn("IA no pudo clasificar reporte #{}: {}",
                                                        saved.getId(), e.getMessage());
                                }

                        } catch (Exception e) {
                                log.warn("Error creando reporte OSINT: {}", e.getMessage());
                        }
                }

                log.info("OSINT pipeline: {} creados, {} clasificados de {} encontrados",
                                created, classified, osintResults.size());

                return Map.of(
                                "city", city,
                                "found", osintResults.size(),
                                "reportsCreated", created,
                                "reportsClassified", classified,
                                "reportIds", reportIds);
        }

        // ═══════════════════════════════════════════════════════════
        // BUSQUEDA: solo buscar sin crear reportes (para preview)
        // ═══════════════════════════════════════════════════════════

        public List<OsintResultDTO> searchIncidents(String city) {
                List<OsintResultDTO> results = new ArrayList<>();

                // Fuente 1 (PRIORIDAD): Paginas Facebook locales de Pasto
                try {
                        List<OsintResultDTO> priorityResults = searchPriorityPages();
                        results.addAll(priorityResults);
                        log.info("FB Prioritarias: {} resultados", priorityResults.size());
                } catch (Exception e) {
                        log.warn("Error en FB prioritarias: {}", e.getMessage());
                }

                // Fuente 2: Google News RSS (gratis, ilimitado)
                try {
                        List<OsintResultDTO> newsResults = searchGoogleNews(city);
                        results.addAll(newsResults);
                        log.info("Google News: {} resultados para '{}'", newsResults.size(), city);
                } catch (Exception e) {
                        log.warn("Error en Google News: {}", e.getMessage());
                }

                // Fuente 3: Facebook busqueda general
                try {
                        List<OsintResultDTO> fbResults = searchFacebook(city);
                        results.addAll(fbResults);
                        log.info("Facebook general: {} resultados para '{}'", fbResults.size(), city);
                } catch (Exception e) {
                        log.warn("Error en Facebook Scraper: {}", e.getMessage());
                }

                log.info("OSINT total: {} resultados combinados", results.size());
                return results;
        }

        // ═══════════════════════════════════════════════════════════
        // FUENTE 1 (PRIORIDAD): Paginas FB locales de Pasto
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
                                if (body == null)
                                        continue;

                                JsonNode root = objectMapper.readTree(body);
                                JsonNode items = root.isArray() ? root : root.path("results");

                                if (items.isArray()) {
                                        for (JsonNode item : items) {
                                                String name = item.has("name") ? item.get("name").asText() : "";
                                                String description = item.has("description")
                                                                ? item.get("description").asText()
                                                                : "";
                                                String pageUrl = item.has("url") ? item.get("url").asText()
                                                                : item.has("link") ? item.get("link").asText() : "";

                                                String content = !description.isBlank() ? description : name;

                                                if (!content.isBlank()) {
                                                        results.add(OsintResultDTO.builder()
                                                                        .title("[FB Prioritaria] " + name)
                                                                        .content(content)
                                                                        .sourceUrl(pageUrl)
                                                                        .sourceType(ReportSource.SOCIAL_MEDIA)
                                                                        .detectedLocation("Pasto")
                                                                        .publishedAt(LocalDateTime.now())
                                                                        .confidence(0.80)
                                                                        .build());
                                                }
                                        }
                                }

                                log.info("FB Prioritaria '{}': OK", pageName);
                        } catch (Exception e) {
                                log.warn("Error scraping '{}': {}", pageName, e.getMessage());
                        }
                }

                return results;
        }

        // ═══════════════════════════════════════════════════════════
        // FUENTE 2: Google News RSS (gratis, sin limite)
        // ═══════════════════════════════════════════════════════════

        private List<OsintResultDTO> searchGoogleNews(String city) {
                List<OsintResultDTO> results = new ArrayList<>();

                String query = "seguridad+" + city.replace(" ", "+") + "+Nariño";
                String url = String.format(
                                "https://news.google.com/rss/search?q=%s&hl=es-419&gl=CO&ceid=CO:es-419",
                                query);

                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                String xml = response.getBody();

                if (xml == null)
                        return results;

                Pattern itemPattern = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
                Matcher itemMatcher = itemPattern.matcher(xml);

                int count = 0;
                while (itemMatcher.find() && count < 10) {
                        String item = itemMatcher.group(1);

                        String title = extractXmlTag(item, "title");
                        String link = extractXmlTag(item, "link");

                        if (isSecurityRelated(title)) {
                                results.add(OsintResultDTO.builder()
                                                .title(title)
                                                .content("Noticia de Google News sobre seguridad en " + city)
                                                .sourceUrl(link)
                                                .sourceType(ReportSource.INSTITUTIONAL)
                                                .detectedLocation(city)
                                                .publishedAt(LocalDateTime.now())
                                                .confidence(0.70)
                                                .build());
                                count++;
                        }
                }

                return results;
        }

        // ═══════════════════════════════════════════════════════════
        // FUENTE 3: Facebook Scraper general via RapidAPI
        // ═══════════════════════════════════════════════════════════

        private List<OsintResultDTO> searchFacebook(String city) {
                List<OsintResultDTO> results = new ArrayList<>();

                String url = String.format(
                                "https://%s/search/posts?query=%s+seguridad&count=10",
                                facebookHost, city.replace(" ", "+"));

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-rapidapi-key", rapidApiKey);
                headers.set("x-rapidapi-host", facebookHost);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> request = new HttpEntity<>(headers);

                try {
                        ResponseEntity<String> response = restTemplate.exchange(
                                        url, HttpMethod.GET, request, String.class);

                        String body = response.getBody();
                        if (body == null)
                                return results;

                        JsonNode root = objectMapper.readTree(body);
                        JsonNode items = root.isArray() ? root : root.path("results");

                        if (items.isArray()) {
                                for (JsonNode item : items) {
                                        String text = item.has("text") ? item.get("text").asText()
                                                        : item.has("message") ? item.get("message").asText()
                                                                        : item.has("name") ? item.get("name").asText()
                                                                                        : "";

                                        String postUrl = item.has("url") ? item.get("url").asText()
                                                        : item.has("link") ? item.get("link").asText() : "";

                                        if (!text.isBlank()) {
                                                results.add(OsintResultDTO.builder()
                                                                .title(text.length() > 100
                                                                                ? text.substring(0, 100) + "..."
                                                                                : text)
                                                                .content(text)
                                                                .sourceUrl(postUrl)
                                                                .sourceType(ReportSource.SOCIAL_MEDIA)
                                                                .detectedLocation(city)
                                                                .publishedAt(LocalDateTime.now())
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

        private boolean isSecurityRelated(String title) {
                if (title == null || title.isBlank())
                        return false;
                String lower = title.toLowerCase();
                return lower.contains("robo") || lower.contains("hurto") || lower.contains("atraco")
                                || lower.contains("accidente") || lower.contains("homicidio")
                                || lower.contains("inseguridad") || lower.contains("policia")
                                || lower.contains("captura") || lower.contains("delincuente")
                                || lower.contains("asalto") || lower.contains("seguridad")
                                || lower.contains("arma") || lower.contains("violencia")
                                || lower.contains("emergencia") || lower.contains("incidente")
                                || lower.contains("transito") || lower.contains("choque");
        }
}
