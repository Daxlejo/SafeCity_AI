package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;
    private final IAClassificationService iaClassificationService;
    private final GeocodingService geocodingService;

    @Transactional
    public ReportResponseDTO createReport(ReportCreateDTO dto) {
        log.info("Creando nuevo reporte de tipo: {}", dto.getIncidentType());

        Report report = convertToEntity(dto);

        // ═══════════════ REVERSE GEOCODING ═══════════════
        // Si el usuario envió coordenadas GPS pero NO dirección,
        // convertimos las coordenadas a nombre de barrio automáticamente
        // Ejemplo: (1.2136, -77.2784) → "Anganoy, Pasto"
        if (report.getLatitude() != null && report.getLongitude() != null
                && (report.getAddress() == null || report.getAddress().isBlank())) {
            String address = geocodingService.reverseGeocode(
                    report.getLatitude(), report.getLongitude());
            report.setAddress(address);
            log.info("Geocoding: ({}, {}) → {}", report.getLatitude(), report.getLongitude(), address);
        }

        Report savedReport = reportRepository.save(report);
        ReportResponseDTO response = convertToDTO(savedReport);

        // Notificar DESPUÉS del save() para garantizar que el reporte existe en BD
        notificationService.notifyNewReport(response);

        // ═══════════════ IA: CLASIFICAR EN BACKGROUND (ASYNC) ═══════════════
        // classifyAsync() corre en otro hilo (Thread Pool "iaExecutor")
        // El usuario recibe respuesta INMEDIATA, la IA trabaja en background
        // Si falla, el reporte queda PENDING para revisión manual
        iaClassificationService.classifyAsync(savedReport.getId());

        log.info("Reporte creado exitosamente con ID: {}", savedReport.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public ReportResponseDTO getReportById(Long id) {
        log.debug("Buscando reporte con ID: {}", id);
        Report report = findReportOrThrow(id);
        return convertToDTO(report);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponseDTO> getAllReports(Pageable pageable) {
        log.debug("Listando reportes - página: {}, tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        return reportRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    // Actualización null-safe: solo modifica campos que el cliente envió
    @Transactional
    public ReportResponseDTO updateReport(Long id, ReportCreateDTO dto) {
        log.info("Actualizando reporte con ID: {}", id);

        Report report = findReportOrThrow(id);
        updateEntityFields(report, dto);
        Report updatedReport = reportRepository.save(report);
        ReportResponseDTO response = convertToDTO(updatedReport);

        notificationService.notifyReportUpdated(response);

        log.info("Reporte ID: {} actualizado exitosamente", id);
        return response;
    }

    @Transactional
    public void deleteReport(Long id) {
        log.info("Eliminando reporte con ID: {}", id);

        if (!reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reporte", "id", id);
        }
        reportRepository.deleteById(id);
        notificationService.notifyReportDeleted(id);

        log.info("Reporte ID: {} eliminado exitosamente", id);
    }

    // Actualizar status de un reporte (para moderacion admin)
    @Transactional
    public void updateStatus(Long id, ReportStatus newStatus) {
        Report report = findReportOrThrow(id);
        report.setStatus(newStatus);
        reportRepository.save(report);
        log.info("Reporte ID: {} actualizado a status: {}", id, newStatus);
    }

    // ═══════════════ HELPERS ═══════════════
    // Nos ayudan a mantener el codigo limpio y organizado

    // DRY: centraliza búsqueda + excepción. Punto único para agregar cache o
    // auditoría.
    private Report findReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Reporte no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Reporte", "id", id);
                });
    }

    // Null-safe: si un campo llega null, preserva el valor existente en BD
    private void updateEntityFields(Report report, ReportCreateDTO dto) {
        if (dto.getDescription() != null)
            report.setDescription(dto.getDescription());
        if (dto.getIncidentType() != null)
            report.setIncidentType(dto.getIncidentType());
        if (dto.getAddress() != null)
            report.setAddress(dto.getAddress());
        if (dto.getSource() != null)
            report.setSource(dto.getSource());
        if (dto.getLatitude() != null)
            report.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null)
            report.setLongitude(dto.getLongitude());
        if (dto.getPhotoUrl() != null)
            report.setPhotoUrl(dto.getPhotoUrl());
    }

    private Report convertToEntity(ReportCreateDTO dto) {
        return Report.builder()
                .description(dto.getDescription())
                .incidentType(dto.getIncidentType())
                .address(dto.getAddress())
                .source(dto.getSource())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .photoUrl(dto.getPhotoUrl())
                .zoneId(dto.getZoneId())
                .status(ReportStatus.PENDING)
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
                .build();
    }
}
