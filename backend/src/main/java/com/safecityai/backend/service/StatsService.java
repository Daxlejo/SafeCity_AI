package com.safecityai.backend.service;

import com.safecityai.backend.dto.DangerousZoneDTO;
import com.safecityai.backend.dto.DangerousZoneSummaryDTO;
import com.safecityai.backend.dto.HeatmapPointDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.dto.StatsSummaryDTO;
import com.safecityai.backend.dto.TypeCountDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
import com.safecityai.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class StatsService {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTES DE CONFIGURACIÓN
    // ═══════════════════════════════════════════════════════════════
    //
    // Precisión de redondeo para el grid de celdas geográficas.
    // 3 decimales ≈ celdas de ~111m en latitud, ~100m en longitud (a latitudes medias).
    // Esto da celdas efectivas de ~200m x 200m como pide el plan.
    //
    private static final double GRID_PRECISION = 1000.0; // 3 decimales
    private static final double CELL_RADIUS_METERS = 100.0; // Radio estimado por celda
    private static final int DANGEROUS_ZONE_DAYS = 7;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;

    /**
     * Backend base URL (e.g. "https://safecity-ai-backend.onrender.com").
     * Used to dynamically build absolute photo URLs in DTO responses.
     * Configured via: app.base-url=${APP_BASE_URL:http://localhost:8080}
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public StatsService(ReportRepository reportRepository,
                        UserRepository userRepository,
                        ZoneRepository zoneRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.zoneRepository = zoneRepository;
    }

    // Resumen completo para el dashboard
    public StatsSummaryDTO getSummary() {
        return StatsSummaryDTO.builder()
                .totalReports(reportRepository.count())
                .pendingReports(reportRepository.countByStatus(ReportStatus.PENDING))
                .verifiedReports(reportRepository.countByStatus(ReportStatus.VERIFIED))
                .rejectedReports(reportRepository.countByStatus(ReportStatus.REJECTED))
                .totalUsers(userRepository.count())
                .totalZones(zoneRepository.count())
                .reportsByType(getReportsByType())
                .heatmapData(getHeatmapData())
                .build();
    }

    // Conteo por tipo → grafica de barras
    public Map<String, Long> getReportsByType() {
        return reportRepository.countByIncidentType().stream()
                .collect(Collectors.toMap(
                        row -> ((IncidentType) row[0]).name(),
                        row -> (Long) row[1]
                ));
    }

    // Conteo por zona → devuelve nombres de zona, no IDs
    public Map<String, Long> getReportsByZone() {
        return reportRepository.countByZoneId().stream()
                .collect(Collectors.toMap(
                        row -> {
                            Long zoneId = (Long) row[0];
                            return zoneRepository.findById(zoneId)
                                    .map(zone -> zone.getName())
                                    .orElse("Zona " + zoneId);
                        },
                        row -> (Long) row[1]
                ));
    }

    // ═══════════════════════════════════════════════════════════════
    // HEATMAP: Clústeres por proximidad (grid-cell ~200m)
    // ═══════════════════════════════════════════════════════════════
    //
    // Algoritmo:
    // 1. Redondear lat/lng a 3 decimales → celdas de ~200m x 200m
    // 2. Agrupar reportes por celda
    // 3. Retornar un punto por celda con weight = cantidad de reportes
    //    e incidentType = tipo más frecuente en esa celda
    //
    public List<HeatmapPointDTO> getHeatmapData() {
        List<Report> allReports = reportRepository.findAllWithCoordinates();
        return buildHeatmapFromReports(allReports);
    }

    /**
     * Construye los puntos del heatmap a partir de una lista de reportes,
     * agrupando por celda geográfica (~200m) y devolviendo el tipo
     * de incidente más frecuente por celda.
     */
    private List<HeatmapPointDTO> buildHeatmapFromReports(List<Report> reports) {
        // Agrupar por celda geográfica
        Map<String, List<Report>> grid = new HashMap<>();
        for (Report r : reports) {
            if (r.getLatitude() == null || r.getLongitude() == null) continue;
            String key = buildGridKey(r.getLatitude(), r.getLongitude());
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // Construir puntos del heatmap
        List<HeatmapPointDTO> points = new ArrayList<>();
        for (Map.Entry<String, List<Report>> entry : grid.entrySet()) {
            List<Report> cellReports = entry.getValue();

            // Centro de la celda (promedio de coordenadas reales)
            double avgLat = cellReports.stream().mapToDouble(Report::getLatitude).average().orElse(0);
            double avgLng = cellReports.stream().mapToDouble(Report::getLongitude).average().orElse(0);

            // Tipo más frecuente en la celda
            String topType = findMostCommonType(cellReports);

            // Weight = cantidad de reportes (normalizado)
            double weight = cellReports.size();

            points.add(HeatmapPointDTO.builder()
                    .latitude(avgLat)
                    .longitude(avgLng)
                    .intensity(weight)
                    .incidentType(topType)
                    .build());
        }

        return points;
    }

    // ═══════════════════════════════════════════════════════════════
    // ZONA MÁS PELIGROSA DE LA SEMANA
    // ═══════════════════════════════════════════════════════════════
    //
    // Usa el mismo grid-cell del heatmap pero solo con reportes
    // de los últimos 7 días. Retorna la celda con mayor cantidad
    // de reportes como la "zona más peligrosa".
    //
    public DangerousZoneSummaryDTO getDangerousZoneOfWeek() {
        LocalDateTime since = LocalDateTime.now().minusDays(DANGEROUS_ZONE_DAYS);
        List<Report> recentReports = reportRepository.findRecentWithFullCoordinates(since);

        if (recentReports.isEmpty()) {
            return null;
        }

        // Agrupar por celda geográfica
        Map<String, List<Report>> grid = new HashMap<>();
        for (Report r : recentReports) {
            if (r.getLatitude() == null || r.getLongitude() == null) continue;
            String key = buildGridKey(r.getLatitude(), r.getLongitude());
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // Encontrar la celda con mayor cantidad de reportes
        Map.Entry<String, List<Report>> topEntry = null;
        for (Map.Entry<String, List<Report>> entry : grid.entrySet()) {
            if (topEntry == null || entry.getValue().size() > topEntry.getValue().size()) {
                topEntry = entry;
            }
        }

        if (topEntry == null) {
            return null;
        }

        List<Report> topCellReports = topEntry.getValue();

        // Centro de la celda
        double avgLat = topCellReports.stream().mapToDouble(Report::getLatitude).average().orElse(0);
        double avgLng = topCellReports.stream().mapToDouble(Report::getLongitude).average().orElse(0);

        // Tipo más frecuente
        String topType = findMostCommonType(topCellReports);

        // Etiqueta descriptiva
        String label = String.format("Zona %.4f, %.4f — %s", avgLat, avgLng, topType);

        return DangerousZoneSummaryDTO.builder()
                .latitude(avgLat)
                .longitude(avgLng)
                .radiusMeters(CELL_RADIUS_METERS)
                .reportCount((long) topCellReports.size())
                .topIncidentType(topType)
                .label(label)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILIDADES PRIVADAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Genera la clave del grid redondeando lat/lng a 3 decimales (~200m).
     */
    private String buildGridKey(double lat, double lng) {
        double roundedLat = Math.round(lat * GRID_PRECISION) / GRID_PRECISION;
        double roundedLng = Math.round(lng * GRID_PRECISION) / GRID_PRECISION;
        return String.format("%.3f,%.3f", roundedLat, roundedLng);
    }

    /**
     * Encuentra el tipo de incidente más frecuente en una lista de reportes.
     */
    private String findMostCommonType(List<Report> reports) {
        return reports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getIncidentType().name(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }

    // Intensidad del punto segun el estado del reporte (legacy, mantenido para compatibilidad)
    private Double calculateIntensity(Report report) {
        return switch (report.getStatus()) {
            case VERIFIED -> 1.0;    // Verificado = maxima intensidad
            case PENDING -> 0.5;     // Pendiente = media
            case RESOLVED -> 0.3;    // Resuelto = baja
            case REJECTED -> 0.1;    // Rechazado = minima
        };
    }

    // Timeline: ultimos N reportes ordenados por fecha descendente
    public List<ReportResponseDTO> getTimeline(int limit) {
        return reportRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "reportDate")))
                .stream()
                .map(r -> ReportResponseDTO.builder()
                        .id(r.getId())
                        .description(r.getDescription())
                        .incidentType(r.getIncidentType())
                        .address(r.getAddress())
                        .status(r.getStatus())
                        .source(r.getSource())
                        .latitude(r.getLatitude())
                        .longitude(r.getLongitude())
                        .photoUrl(getFullPhotoUrl(r.getPhotoUrl()))
                        .trustScore(r.getTrustScore())
                        .zoneId(r.getZoneId())
                        .reportDate(r.getReportDate())
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // RANKING DE ZONAS PELIGROSAS SEMANAL
    // ═══════════════════════════════════════════════════════════════
    //
    // ¿Cómo agrupamos reportes por "zona" sin tener zonas definidas?
    // ────────────────────────────────────────────────────────────────
    // Usamos Geographic Grid Clustering:
    // 1. Redondeamos lat/lng a 2 decimales (≈1.1km de área)
    // 2. Los reportes con la misma lat/lng redondeada están en la misma "celda"
    // 3. Contamos incidentes por celda
    // 4. Rankeamos de mayor a menor
    //
    // Esto es un algoritmo O(n) — mucho más eficiente que comparar
    // distancias entre todos los pares de reportes O(n²)
    //
    public List<DangerousZoneDTO> getDangerousZones(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Report> recentReports = reportRepository.findRecentWithCoordinates(since);

        // Agrupar por "celda" geográfica (lat/lng redondeado a 2 decimales)
        Map<String, List<Report>> grid = new HashMap<>();
        for (Report r : recentReports) {
            if (r.getLatitude() == null || r.getLongitude() == null) continue;
            // Redondear a 2 decimales ≈ celdas de ~1km
            String key = String.format("%.2f,%.2f", r.getLatitude(), r.getLongitude());
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // Construir ranking
        List<DangerousZoneDTO> ranking = new ArrayList<>();
        for (var entry : grid.entrySet()) {
            List<Report> cellReports = entry.getValue();
            long count = cellReports.size();

            // Encontrar el tipo más común en esta celda
            String mostCommon = findMostCommonType(cellReports);

            // Nivel de riesgo según cantidad de incidentes
            String risk;
            if (count >= 5) risk = "HIGH";
            else if (count >= 3) risk = "MEDIUM";
            else risk = "LOW";

            String[] coords = entry.getKey().split(",");
            String areaName = String.format("Zona %.2f, %.2f",
                    Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));

            ranking.add(DangerousZoneDTO.builder()
                    .areaName(areaName)
                    .incidentCount(count)
                    .mostCommonType(mostCommon)
                    .riskLevel(risk)
                    .build());
        }

        // Ordenar por cantidad de incidentes (descendente)
        ranking.sort((a, b) -> Long.compare(b.getIncidentCount(), a.getIncidentCount()));

        // Asignar posición en ranking y limitar resultados
        List<DangerousZoneDTO> topN = ranking.stream()
                .limit(limit)
                .collect(Collectors.toList());
        for (int i = 0; i < topN.size(); i++) {
            topN.get(i).setRank(i + 1);
        }

        return topN;
    }

    // ═══════════════ PHOTO URL HELPER ═══════════════

    /**
     * Builds the absolute photo URL to return to the client.
     * - If stored value is already a full URL (starts with http:// or https://), returns it unchanged
     *   for backward-compatibility with legacy DB rows.
     * - Otherwise, prepends baseUrl + "/api/v1/uploads/" to the raw filename.
     * - Returns null if the stored value is null or blank.
     */
    private String getFullPhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
            return photoUrl;
        }
        return baseUrl + "/api/v1/uploads/" + photoUrl;
    }
}
