package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para transferir la configuración OSINT entre backend y frontend.
 * Las listas de keywords y priorityUrls se manejan como List<String> en el DTO
 * y se serializan/deserializan desde JSON string en la entidad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsintConfigDTO {

    private Long id;

    /** Indica si el scheduler automático está activo */
    private boolean enabled;

    /** Intervalo entre scans en minutos */
    private int intervalMinutes;

    /** Palabras clave de búsqueda para fuentes OSINT */
    private List<String> keywords;

    /** Nombres/URLs de fuentes prioritarias (páginas de Facebook, etc.) */
    private List<String> priorityUrls;

    /** Ciudad por defecto para búsquedas */
    private String defaultCity;

    /** Máximo de ítems a procesar por ejecución */
    private int maxItemsPerExecution;

    /** Última vez que se actualizó la configuración */
    private LocalDateTime updatedAt;
}
