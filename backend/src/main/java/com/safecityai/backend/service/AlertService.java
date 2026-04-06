package com.safecityai.backend.service;

import com.safecityai.backend.dto.AlertPreferenceDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.AlertPreference;
import com.safecityai.backend.model.User;
import com.safecityai.backend.repository.AlertPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private final AlertPreferenceRepository alertRepo;

    public AlertService(AlertPreferenceRepository alertRepo) {
        this.alertRepo = alertRepo;
    }

    // Obtener preferencias de un usuario
    public List<AlertPreferenceDTO> getPreferences(Long userId) {
        return alertRepo.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Obtener solo las preferencias activas
    public List<AlertPreferenceDTO> getActivePreferences(Long userId) {
        return alertRepo.findByUserIdAndEnabledTrue(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Crear o actualizar una preferencia
    @Transactional
    public AlertPreferenceDTO savePreference(Long userId, User user, AlertPreferenceDTO dto) {
        // Verifica si ya existe para ese tipo
        AlertPreference pref = alertRepo.findByUserIdAndIncidentType(userId, dto.getIncidentType())
                .orElse(AlertPreference.builder()
                        .user(user)
                        .incidentType(dto.getIncidentType())
                        .build());

        pref.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        return toDTO(alertRepo.save(pref));
    }

    // Eliminar una preferencia
    @Transactional
    public void deletePreference(Long id) {
        if (!alertRepo.existsById(id)) {
            throw new ResourceNotFoundException("Preferencia con id " + id + " no encontrada");
        }
        alertRepo.deleteById(id);
    }

    private AlertPreferenceDTO toDTO(AlertPreference pref) {
        AlertPreferenceDTO dto = new AlertPreferenceDTO();
        dto.setId(pref.getId());
        dto.setIncidentType(pref.getIncidentType());
        dto.setEnabled(pref.getEnabled());
        return dto;
    }
}
