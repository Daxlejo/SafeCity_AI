package com.safecityai.backend.service;

import com.safecityai.backend.dto.HeatmapPointDTO;
import com.safecityai.backend.dto.StatsSummaryDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
import com.safecityai.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
                .byType(getReportsByType())
                .byZone(getReportsByZoneNames())
                .timeline(getTimeline())
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

    // Conteo por zona
    public java.util.Map<Long, Long> getReportsByZone() {
        return reportRepository.countByZoneId().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    // Conteo por zona extraido con Nombres 
    public java.util.Map<String, Long> getReportsByZoneNames() {
        return reportRepository.countByZoneId().stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.toMap(
                        row -> zoneRepository.findById((Long) row[0])
                                .map(z -> z.getName())
                                .orElse("Desconocida"),
                        row -> (Long) row[1],
                        (existing, replacement) -> existing
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

    // Datos de los últimos 7 días
    public List<java.util.Map<String, Object>> getTimeline() {
        java.time.LocalDateTime sevenDaysAgo = java.time.LocalDateTime.now().minusDays(7);
        // Traemos los recientes sin queries complejas para evitar conflictos de dialecto
        List<Report> recentReports = reportRepository.findAll()
                .stream()
                .filter(r -> r.getReportDate() != null && r.getReportDate().isAfter(sevenDaysAgo))
                .toList();

        java.util.Map<java.time.LocalDate, Long> countsByDate = recentReports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getReportDate().toLocalDate(),
                        Collectors.counting()
                ));

        List<java.util.Map<String, Object>> timeline = new java.util.ArrayList<>();
        // Asegurar 7 días incluyendo ceros
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate date = java.time.LocalDate.now().minusDays(i);
            java.util.Map<String, Object> dayStat = new java.util.HashMap<>();
            dayStat.put("date", date.toString());
            dayStat.put("count", countsByDate.getOrDefault(date, 0L));
            timeline.add(dayStat);
        }
        return timeline;
    }
}
