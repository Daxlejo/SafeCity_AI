package com.safecityai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Reverse Geocoding: convierte coordenadas GPS → nombre de barrio/sector.
 *
 * ¿Qué es Reverse Geocoding?
 * ───────────────────────────
 * Es el proceso INVERSO de la geocodificación normal:
 *   - Normal:  "Calle 18 #25-46, Pasto" → (1.2136, -77.2784)
 *   - Reverse: (1.2136, -77.2784) → "Anganoy, Pasto"
 *
 * API utilizada: Nominatim (OpenStreetMap)
 *   - Gratuita, sin API key
 *   - Requiere User-Agent (política de uso justo)
 *   - Rate limit: 1 request/segundo (suficiente para nuestro caso)
 *
 * ¿Por qué Nominatim y no Google Maps?
 *   - Google Maps Geocoding API cobra después de 40,000 requests/mes
 *   - Nominatim es gratis y open source
 *   - Para un proyecto universitario, es la mejor opción
 */
@Slf4j
@Service
public class GeocodingService {

    private static final String NOMINATIM_REVERSE_URL =
            "https://nominatim.openstreetmap.org/reverse?lat=%s&lon=%s&format=json&addressdetails=1&accept-language=es";

    private static final String NOMINATIM_SEARCH_URL =
            "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1&accept-language=es";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ConcurrentHashMap<String, String> reverseCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, double[]> forwardCache = new java.util.concurrent.ConcurrentHashMap<>();

    public GeocodingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Convierte coordenadas a nombre legible de barrio/sector.
     *
     * Prioridad de extracción del JSON de Nominatim:
     *   1. neighbourhood (barrio exacto, ej: "Anganoy")
     *   2. suburb (sector/comuna, ej: "Comuna 10")
     *   3. city_district (distrito, ej: "Centro")
     *   4. city/town (ciudad, ej: "Pasto")
     *
     * Si la API falla → retorna "Zona {lat}, {lng}" como fallback seguro.
     * NUNCA lanza excepción — el reporte se crea igual sin dirección bonita.
     */
    public String reverseGeocode(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return null;
        }
        String cacheKey = lat + "," + lng;
        if (reverseCache.containsKey(cacheKey)) {
            return reverseCache.get(cacheKey);
        }

        try {
            String url = String.format(NOMINATIM_REVERSE_URL, lat, lng);

            HttpHeaders headers = new HttpHeaders();
            // Nominatim REQUIERE un User-Agent descriptivo (política de uso)
            headers.set("User-Agent", "SafeCityAI/1.0 (proyecto universitario)");

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getBody() == null) {
                return formatFallback(lat, lng);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode address = root.path("address");

            // Extraer el nombre más específico disponible
            String neighbourhood = address.path("neighbourhood").asText(null);
            String suburb = address.path("suburb").asText(null);
            String cityDistrict = address.path("city_district").asText(null);
            String city = address.path("city").asText(
                    address.path("town").asText(null));

            // Construir el nombre: "Barrio, Ciudad"
            String specificArea = neighbourhood != null ? neighbourhood
                    : suburb != null ? suburb
                    : cityDistrict != null ? cityDistrict
                    : null;

            if (specificArea != null && city != null) {
                String result = specificArea + ", " + city;
                reverseCache.put(cacheKey, result);
                return result;
            } else if (specificArea != null) {
                reverseCache.put(cacheKey, specificArea);
                return specificArea;
            } else if (city != null) {
                reverseCache.put(cacheKey, city);
                return city;
            }

            String fallback = formatFallback(lat, lng);
            reverseCache.put(cacheKey, fallback);
            return fallback;

        } catch (Exception e) {
            // NUNCA rompemos el flujo del reporte por un error de geocoding
            log.warn("[Geocoding] Error resolviendo ({}, {}): {}", lat, lng, e.getMessage());
            String fallback = formatFallback(lat, lng);
            reverseCache.put(cacheKey, fallback);
            return fallback;
        }
    }

    private String formatFallback(Double lat, Double lng) {
        return String.format("Zona %.4f, %.4f", lat, lng);
    }

    /**
     * Convierte un texto a coordenadas (Geocoding).
     * Retorna [latitud, longitud] o null si falla.
     */
    public double[] geocode(String address) {
        if (address == null || address.isBlank()) return null;
        if (forwardCache.containsKey(address)) return forwardCache.get(address);

        try {
            String url = String.format(NOMINATIM_SEARCH_URL, address.replace(" ", "+"));

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SafeCityAI/1.0 (proyecto universitario)");
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getBody() == null) return null;

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                double lat = first.path("lat").asDouble();
                double lon = first.path("lon").asDouble();
                double[] coords = new double[]{lat, lon};
                forwardCache.put(address, coords);
                return coords;
            }
        } catch (Exception e) {
            log.warn("[Geocoding] Error resolviendo '{}': {}", address, e.getMessage());
        }
        return null;
    }
}
