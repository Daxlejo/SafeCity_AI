package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio que encapsula la lógica de negocio para reportes.
 * 
 * Fusionado: Incluye CRUD básico, notificaciones en tiempo real (WebSockets)
 * y consultas geoespaciales (Haversine/Bounding Box).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;

    // ──────────── CRUD BÁSICO (Sprint 2 / Origin) ────────────

    @Transactional
    public ReportResponseDTO createReport(ReportCreateDTO dto) {
        log.info("Creando nuevo reporte de tipo: {}", dto.getIncidentType());

        Report report = convertToEntity(dto);
        Report savedReport = reportRepository.save(report);
        ReportResponseDTO response = convertToDTO(savedReport);

        // Notificar via WebSocket DESPUÉS del save()
        notificationService.notifyNewReport(response);

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

    // ──────────── CONSULTAS GEOESPACIALES (Sprint 2 / Geospatial) ────────────

    /**
     * Busca reportes cercanos a un punto geográfico (Haversine).
     */
    @Transactional(readOnly = true)
    public List<ReportResponseDTO> findNearbyReports(double lat, double lng, double radiusKm) {
        validateCoordinates(lat, lng);
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("El radio debe ser mayor a 0 km");
        }

        log.debug("Buscando reportes cerca de [{}, {}] en radio de {}km", lat, lng, radiusKm);
        List<Report> reports = reportRepository.findNearby(lat, lng, radiusKm);
        return reports.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca reportes dentro de una zona rectangular (bounding box).
     */
    @Transactional(readOnly = true)
    public List<ReportResponseDTO> findReportsByZone(double latMin, double latMax,
                                                     double lngMin, double lngMax) {
        if (latMin > latMax) throw new IllegalArgumentException("latMin > latMax");
        if (lngMin > lngMax) throw new IllegalArgumentException("lngMin > lngMax");
        
        validateCoordinates(latMin, lngMin);
        validateCoordinates(latMax, lngMax);

        log.debug("Buscando reportes en zona: lat[{} a {}], lng[{} a {}]", latMin, latMax, lngMin, lngMax);
        List<Report> reports = reportRepository.findByZone(latMin, latMax, lngMin, lngMax);
        return reports.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ──────────── HELPERS ────────────

    private Report findReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Reporte no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Reporte", "id", id);
                });
    }

    private void updateEntityFields(Report report, ReportCreateDTO dto) {
        if (dto.getDescription() != null) report.setDescription(dto.getDescription());
        if (dto.getIncidentType() != null) report.setIncidentType(dto.getIncidentType());
        if (dto.getAddress() != null) report.setAddress(dto.getAddress());
        if (dto.getSource() != null) report.setSource(dto.getSource());
        if (dto.getLatitude() != null) report.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) report.setLongitude(dto.getLongitude());
    }

    private Report convertToEntity(ReportCreateDTO dto) {
        return Report.builder()
                .description(dto.getDescription())
                .incidentType(dto.getIncidentType())
                .address(dto.getAddress())
                .source(dto.getSource())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
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
                .reportDate(report.getReportDate())
                .build();
    }

    private void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + lat);
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + lng);
        }
    }
}
