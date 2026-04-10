package com.safecityai.backend.service;

import com.safecityai.backend.dto.HeatmapPointDTO;
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

    // Conteo por zona
    public java.util.Map<Long, Long> getReportsByZone() {
        return reportRepository.countByZoneId().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
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
}
