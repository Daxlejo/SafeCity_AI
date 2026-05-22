package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.dto.ZoneCreateDTO;
import com.safecityai.backend.dto.ZoneResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Zone;
import com.safecityai.backend.model.enums.RiskLevel;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final ReportRepository reportRepository;

    /**
     * Backend base URL (e.g. "https://safecity-ai-backend.onrender.com").
     * Used to dynamically build absolute photo URLs in DTO responses.
     * Configured via: app.base-url=${APP_BASE_URL:http://localhost:8080}
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ZoneService(ZoneRepository zoneRepository, ReportRepository reportRepository) {
        this.zoneRepository = zoneRepository;
        this.reportRepository = reportRepository;
    }

    // Listar todas las zonas
    public List<ZoneResponseDTO> findAll() {
        return zoneRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Buscar zona por ID
    public ZoneResponseDTO findById(Long id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zona con id " + id + " no encontrada"));
        return toDTO(zone);
    }

    // Solo zonas peligrosas (HIGH + CRITICAL)
    public List<ZoneResponseDTO> findRisky() {
        return zoneRepository.findByRiskLevelIn(List.of(RiskLevel.HIGH, RiskLevel.CRITICAL))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Timeline: reportes dentro del area de la zona, ordenados por fecha
    public List<ReportResponseDTO> getZoneTimeline(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zona con id " + zoneId + " no encontrada"));

        // Convertir radio (metros) a grados aproximados
        double radiusInDegrees = zone.getRadius() / 111_320.0;
        double minLat = zone.getCenterLat() - radiusInDegrees;
        double maxLat = zone.getCenterLat() + radiusInDegrees;
        double minLng = zone.getCenterLng() - radiusInDegrees;
        double maxLng = zone.getCenterLng() + radiusInDegrees;

        return reportRepository.findByArea(minLat, maxLat, minLng, maxLng).stream()
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
                        .reportDate(r.getReportDate())
                        .build())
                .collect(Collectors.toList());
    }

    // Crear nueva zona (solo ADMIN)
    @Transactional
    public ZoneResponseDTO create(ZoneCreateDTO dto) {
        Zone zone = Zone.builder()
                .name(dto.getName())
                .centerLat(dto.getCenterLat())
                .centerLng(dto.getCenterLng())
                .radius(dto.getRadius() != null ? dto.getRadius() : 500.0)
                .riskLevel(dto.getRiskLevel() != null ? dto.getRiskLevel() : RiskLevel.LOW)
                .lastUpdated(LocalDateTime.now())
                .build();

        return toDTO(zoneRepository.save(zone));
    }

    // Actualizar nivel de riesgo de una zona
    @Transactional
    public ZoneResponseDTO updateRiskLevel(Long id, RiskLevel newLevel) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zona con id " + id + " no encontrada"));

        zone.setRiskLevel(newLevel);
        zone.setLastUpdated(LocalDateTime.now());
        return toDTO(zoneRepository.save(zone));
    }

    // Eliminar zona
    @Transactional
    public void delete(Long id) {
        if (!zoneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Zona con id " + id + " no encontrada");
        }
        zoneRepository.deleteById(id);
    }

    private ZoneResponseDTO toDTO(Zone zone) {
        return ZoneResponseDTO.builder()
                .id(zone.getId())
                .name(zone.getName())
                .riskLevel(zone.getRiskLevel())
                .centerLat(zone.getCenterLat())
                .centerLng(zone.getCenterLng())
                .radius(zone.getRadius())
                .reportCount(zone.getReportCount())
                .lastUpdated(zone.getLastUpdated())
                .build();
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
