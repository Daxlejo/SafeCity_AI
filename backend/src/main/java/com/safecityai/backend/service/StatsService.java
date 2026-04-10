package com.safecityai.backend.service;

import com.safecityai.backend.dto.StatsSummaryDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
import com.safecityai.backend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ReportRepository reportRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;

    // Resumen general: totales + breakdown por tipo + por zona + timeline
    @Transactional(readOnly = true)
    public StatsSummaryDTO getSummary() {
        log.debug("Generando resumen de estadísticas");

        List<Report> allReports = reportRepository.findAll();

        return StatsSummaryDTO.builder()
                .totalReports((long) allReports.size())
                .totalZones(zoneRepository.count())
                .totalUsers(userRepository.count())
                .byType(countByType(allReports))
                .byZone(countByZone(allReports))
                .timeline(buildTimeline(allReports))
                .build();
    }

    // Conteo por tipo de incidente
    @Transactional(readOnly = true)
    public Map<String, Long> getByType() {
        return countByType(reportRepository.findAll());
    }

    // Conteo por zona
    @Transactional(readOnly = true)
    public Map<String, Long> getByZone() {
        return countByZone(reportRepository.findAll());
    }

    // Tendencia de los últimos 7 días
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTimeline() {
        return buildTimeline(reportRepository.findAll());
    }

    // ═══════════════ HELPERS ═══════════════

    private Map<String, Long> countByType(List<Report> reports) {
        return reports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getIncidentType().name(),
                        Collectors.counting()
                ));
    }

    private Map<String, Long> countByZone(List<Report> reports) {
        return reports.stream()
                .filter(r -> r.getZone() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getZone().getName(),
                        Collectors.counting()
                ));
    }

    private List<Map<String, Object>> buildTimeline(List<Report> reports) {
        // Últimos 7 días
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            long count = reports.stream()
                    .filter(r -> r.getReportDate() != null)
                    .filter(r -> !r.getReportDate().isBefore(startOfDay) &&
                                 !r.getReportDate().isAfter(endOfDay))
                    .count();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", date.toString());
            entry.put("count", count);
            timeline.add(entry);
        }

        return timeline;
    }
}
