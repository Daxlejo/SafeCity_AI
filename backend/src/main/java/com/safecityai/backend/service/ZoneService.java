package com.safecityai.backend.service;

import com.safecityai.backend.dto.ZoneCreateDTO;
import com.safecityai.backend.dto.ZoneResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Zone;
import com.safecityai.backend.model.enums.RiskLevel;
import com.safecityai.backend.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
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
}
