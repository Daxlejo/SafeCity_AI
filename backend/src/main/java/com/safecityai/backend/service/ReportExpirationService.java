package com.safecityai.backend.service;

import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.ReportExpirationConfig;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportExpirationConfigRepository;
import com.safecityai.backend.repository.ReportRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExpirationService {

    private final ReportExpirationConfigRepository configRepository;
    private final ReportRepository reportRepository;
    private final ReportService reportService;

    @PostConstruct
    public void initDefaultConfigs() {
        if (configRepository.count() == 0) {
            log.info("Inicializando configuración por defecto para expiración de reportes");
            configRepository.save(new ReportExpirationConfig(null, IncidentType.ROBBERY, 24));
            configRepository.save(new ReportExpirationConfig(null, IncidentType.ACCIDENT, 2));
            configRepository.save(new ReportExpirationConfig(null, IncidentType.TRAFFIC, 1));
            configRepository.save(new ReportExpirationConfig(null, IncidentType.TRANSIT_OP, 4));
            configRepository.save(new ReportExpirationConfig(null, IncidentType.OTHER, 12));
        }
    }

    public List<ReportExpirationConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    @Transactional
    public ReportExpirationConfig updateConfig(IncidentType incidentType, Integer expirationHours) {
        ReportExpirationConfig config = configRepository.findByIncidentType(incidentType)
                .orElse(new ReportExpirationConfig(null, incidentType, expirationHours));
        config.setExpirationHours(expirationHours);
        return configRepository.save(config);
    }

    @Scheduled(cron = "0 0/15 * * * *") // Cada 15 minutos
    @Transactional
    public void expireOldReports() {
        log.info("Ejecutando job de expiración de reportes...");
        List<ReportExpirationConfig> configs = configRepository.findAll();
        Map<IncidentType, Integer> expirationMap = configs.stream()
                .collect(Collectors.toMap(ReportExpirationConfig::getIncidentType, ReportExpirationConfig::getExpirationHours));

        // Buscar reportes que puedan expirar (activos: PENDING, VERIFIED)
        List<ReportStatus> activeStatuses = List.of(ReportStatus.PENDING, ReportStatus.VERIFIED);
        List<Report> activeReports = reportRepository.findByStatusIn(activeStatuses);
        
        int expiredCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Report report : activeReports) {
            Integer expirationHours = expirationMap.get(report.getIncidentType());
            if (expirationHours != null && expirationHours > 0) {
                // reportDate (creation) + expirationHours
                LocalDateTime expiresAt = report.getReportDate().plusHours(expirationHours);
                if (now.isAfter(expiresAt)) {
                    reportService.updateStatus(report.getId(), ReportStatus.EXPIRED);
                    expiredCount++;
                }
            }
        }
        
        if (expiredCount > 0) {
            log.info("Job de expiración finalizado. {} reportes marcados como EXPIRED.", expiredCount);
        } else {
            log.debug("Job de expiración finalizado. No hay reportes para expirar.");
        }
    }
}
