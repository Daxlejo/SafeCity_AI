package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.enums.ReportSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio OSINT (Open Source Intelligence).
 * 
 * Fase 1 (actual): Datos simulados para demo
 * Fase 2 (futuro): Integrar APIs reales de noticias (NewsAPI, Google News)
 *                   y redes sociales (Twitter/X API, Facebook Graph)
 * 
 * El scraping real se implementara con:
 * - Jsoup (HTML parsing para sitios de noticias locales)
 * - APIs de redes sociales
 * - Programacion de tareas con @Scheduled (cron jobs)
 */
@Service
public class OsintService {

    /**
     * Busca incidentes en fuentes abiertas para una ciudad.
     * Fase 1: retorna datos simulados para Pasto.
     */
    public List<OsintResultDTO> searchIncidents(String city) {
        // Fase 1: datos simulados para demo
        List<OsintResultDTO> results = new ArrayList<>();

        results.add(OsintResultDTO.builder()
                .title("Reporte de robo en el centro de " + city)
                .content("Ciudadanos reportan un intento de hurto en la zona centro de la ciudad. Las autoridades se hicieron presentes.")
                .sourceUrl("https://noticias-locales.co/seguridad/" + city.toLowerCase())
                .sourceType(ReportSource.SOCIAL_MEDIA)
                .detectedLocation("Centro de " + city)
                .latitude(1.2136)
                .longitude(-77.2811)
                .publishedAt(LocalDateTime.now().minusHours(2))
                .confidence(0.75)
                .build());

        results.add(OsintResultDTO.builder()
                .title("Accidente de transito reportado via redes sociales")
                .content("Un accidente vehicular fue reportado en la avenida principal. Se recomienda tomar rutas alternas.")
                .sourceUrl("https://twitter.com/transitopasto/status/example")
                .sourceType(ReportSource.SOCIAL_MEDIA)
                .detectedLocation("Av. Principal, " + city)
                .latitude(1.2205)
                .longitude(-77.2750)
                .publishedAt(LocalDateTime.now().minusHours(1))
                .confidence(0.60)
                .build());

        results.add(OsintResultDTO.builder()
                .title("Informe policial de operativo de transito")
                .content("La policia realizo un operativo de control vehicular en la zona sur de la ciudad con resultados positivos.")
                .sourceUrl("https://policia.gov.co/noticias/" + city.toLowerCase())
                .sourceType(ReportSource.INSTITUTIONAL)
                .detectedLocation("Zona Sur, " + city)
                .latitude(1.2050)
                .longitude(-77.2900)
                .publishedAt(LocalDateTime.now().minusMinutes(30))
                .confidence(0.90)
                .build());

        return results;
    }
}
