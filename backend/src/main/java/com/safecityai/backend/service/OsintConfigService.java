package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintConfigDTO;
import com.safecityai.backend.model.OsintConfig;
import com.safecityai.backend.repository.OsintConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio para gestionar la configuración OSINT.
 * Patrón Singleton-Row: siempre existe exactamente una fila de configuración.
 * Si no existe, se crea con valores por defecto.
 */
@Slf4j
@Service
public class OsintConfigService {

    private final OsintConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public OsintConfigService(OsintConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Obtiene la configuración activa del módulo OSINT.
     * Si no existe ninguna fila, la crea con valores por defecto.
     */
    @Transactional
    public OsintConfig getActiveConfig() {
        return configRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    log.info("[OsintConfig] No existe configuración, creando con valores por defecto");
                    return configRepository.save(OsintConfig.builder().build());
                });
    }

    /**
     * Obtiene la configuración como DTO para el frontend.
     */
    public OsintConfigDTO getConfigDTO() {
        OsintConfig config = getActiveConfig();
        return toDTO(config);
    }

    /**
     * Actualiza la configuración OSINT desde un DTO del frontend.
     */
    @Transactional
    public OsintConfigDTO updateConfig(OsintConfigDTO dto) {
        OsintConfig config = getActiveConfig();

        config.setEnabled(dto.isEnabled());
        config.setIntervalMinutes(Math.max(1, dto.getIntervalMinutes()));
        config.setDefaultCity(dto.getDefaultCity() != null ? dto.getDefaultCity() : "Pasto");
        config.setMaxItemsPerExecution(Math.max(1, Math.min(50, dto.getMaxItemsPerExecution())));

        // Convertir listas a JSON string para persistir
        if (dto.getKeywords() != null) {
            config.setKeywords(toJsonString(dto.getKeywords()));
        }
        if (dto.getPriorityUrls() != null) {
            config.setPriorityUrls(toJsonString(dto.getPriorityUrls()));
        }

        OsintConfig saved = configRepository.save(config);
        log.info("[OsintConfig] Configuración actualizada: enabled={}, interval={}min, keywords={}, city={}",
                saved.isEnabled(), saved.getIntervalMinutes(),
                saved.getKeywords(), saved.getDefaultCity());

        return toDTO(saved);
    }

    /**
     * Activa o desactiva rápidamente el scheduler OSINT.
     */
    @Transactional
    public OsintConfigDTO toggleEnabled(boolean enabled) {
        OsintConfig config = getActiveConfig();
        config.setEnabled(enabled);
        OsintConfig saved = configRepository.save(config);
        log.info("[OsintConfig] Scheduler OSINT {} por admin", enabled ? "ACTIVADO" : "DESACTIVADO");
        return toDTO(saved);
    }

    // ═══════════════════════════════════════════
    // Conversión Entity ↔ DTO
    // ═══════════════════════════════════════════

    private OsintConfigDTO toDTO(OsintConfig config) {
        return OsintConfigDTO.builder()
                .id(config.getId())
                .enabled(config.isEnabled())
                .intervalMinutes(config.getIntervalMinutes())
                .keywords(fromJsonString(config.getKeywords()))
                .priorityUrls(fromJsonString(config.getPriorityUrls()))
                .defaultCity(config.getDefaultCity())
                .maxItemsPerExecution(config.getMaxItemsPerExecution())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private String toJsonString(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("[OsintConfig] Error serializando lista a JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private List<String> fromJsonString(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[OsintConfig] Error deserializando JSON a lista: {}", e.getMessage());
            return List.of();
        }
    }
}
