package com.safecityai.backend.service;

import com.safecityai.backend.dto.DangerousZoneDTO;
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

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class StatsService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;

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
    public java.util.Map<String, Long> getReportsByType() {
        return reportRepository.countByIncidentType().stream()
                .collect(Collectors.toMap(
                        row -> ((IncidentType) row[0]).name(),
                        row -> (Long) row[1]
                ));
    }

    // Conteo por zona → devuelve nombres de zona, no IDs
    public java.util.Map<String, Long> getReportsByZone() {
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

    // Datos para el heatmap del frontend
    public List<HeatmapPointDTO> getHeatmapData() {
        return reportRepository.findAllWithCoordinates().stream()
                .map(report -> HeatmapPointDTO.builder()
                        .latitude(report.getLatitude())
                        .longitude(report.getLongitude())
                        .intensity(calculateIntensity(report))
                        .build())
                .collect(Collectors.toList());
    }

    // Intensidad del punto segun el estado del reporte
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
                        .photoUrl(r.getPhotoUrl())
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
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(days);
        List<Report> recentReports = reportRepository.findRecentWithCoordinates(since);

        // Agrupar por "celda" geográfica (lat/lng redondeado a 2 decimales)
        java.util.Map<String, List<Report>> grid = new java.util.HashMap<>();
        for (Report r : recentReports) {
            if (r.getLatitude() == null || r.getLongitude() == null) continue;
            // Redondear a 2 decimales ≈ celdas de ~1km
            String key = String.format("%.2f,%.2f", r.getLatitude(), r.getLongitude());
            grid.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(r);
        }

        // Construir ranking
        List<DangerousZoneDTO> ranking = new java.util.ArrayList<>();
        for (var entry : grid.entrySet()) {
            List<Report> cellReports = entry.getValue();
            long count = cellReports.size();

            // Encontrar el tipo más común en esta celda
            String mostCommon = cellReports.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getIncidentType().name(),
                            Collectors.counting()))
                    .entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("OTHER");

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
}

