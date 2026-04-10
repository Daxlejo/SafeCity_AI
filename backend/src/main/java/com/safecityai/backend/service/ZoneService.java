package com.safecityai.backend.service;

import com.safecityai.backend.dto.ZoneCreateDTO;
import com.safecityai.backend.dto.ZoneResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Zone;
import com.safecityai.backend.model.enums.RiskLevel;
import com.safecityai.backend.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional
    public ZoneResponseDTO createZone(ZoneCreateDTO dto) {
        log.info("Creando nueva zona: {}", dto.getName());

        Zone zone = Zone.builder()
                .name(dto.getName())
                .centerLat(dto.getCenterLat())
                .centerLng(dto.getCenterLng())
                .radius(dto.getRadius())
                .build();

        Zone saved = zoneRepository.save(zone);
        log.info("Zona creada con ID: {}", saved.getId());
        return convertToDTO(saved);
    }

    @Transactional(readOnly = true)
    public ZoneResponseDTO getZoneById(Long id) {
        Zone zone = findZoneOrThrow(id);
        return convertToDTO(zone);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponseDTO> getAllZones() {
        return zoneRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ZoneResponseDTO> getRiskyZones() {
        List<RiskLevel> riskyLevels = List.of(RiskLevel.HIGH, RiskLevel.CRITICAL);
        return zoneRepository.findByRiskLevelIn(riskyLevels).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Incrementa el contador de reportes y recalcula el nivel de riesgo
    @Transactional
    public void incrementReportCount(Long zoneId) {
        Zone zone = findZoneOrThrow(zoneId);
        zone.setReportCount(zone.getReportCount() + 1);
        zone.setRiskLevel(calculateRiskLevel(zone.getReportCount()));
        zoneRepository.save(zone);
        log.info("Zona ID: {} actualizada → reportCount={}, riskLevel={}",
                zoneId, zone.getReportCount(), zone.getRiskLevel());
    }

    // Calcula el nivel de riesgo en base a la cantidad de reportes
    private RiskLevel calculateRiskLevel(int reportCount) {
        if (reportCount >= 10) return RiskLevel.CRITICAL;
        if (reportCount >= 5)  return RiskLevel.HIGH;
        if (reportCount >= 2)  return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private Zone findZoneOrThrow(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Zona no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Zona", "id", id);
                });
    }

    private ZoneResponseDTO convertToDTO(Zone zone) {
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
}
