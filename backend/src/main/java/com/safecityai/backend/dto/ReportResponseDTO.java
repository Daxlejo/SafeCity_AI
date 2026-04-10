package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para DEVOLVER un reporte al frontend.
 *
 * Diferencias clave con ReportCreateDTO:
 * 1. INCLUYE id → el frontend lo necesita para GET /reports/{id}, PUT, DELETE
 * 2. INCLUYE status → el ciudadano ve si su reporte está PENDING, VERIFIED,
 * etc.
 * 3. INCLUYE reportDate → para mostrar cuándo se creó el reporte
 * 4. NO tiene anotaciones de validación → no validamos lo que SALE, solo lo que
 * ENTRA
 *
 * ¿Por qué no devolver la entidad Report directamente?
 * - La entidad podría tener relaciones JPA que causan serialización infinita
 * - Si mañana la entidad agrega un campo sensible, no se expondría por
 * accidente
 * - El DTO es un "contrato" con el frontend: si la entidad cambia, el DTO se
 * mantiene
 */
@Data
@Builder // Permite construir objetos así: ReportResponseDTO.builder().id(1L).build()
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDTO {

    /**
     * ID único del reporte, generado por la BD.
     * El frontend lo necesita para operaciones específicas:
     * GET /reports/{id}, PUT /reports/{id}, DELETE /reports/{id}
     */
    private Long id;

    private String description;

    /**
     * Tipo de incidente. El frontend puede usar esto para:
     * - Mostrar iconos distintos en el mapa según el tipo
     * - Filtrar reportes por categoría
     */
    private IncidentType incidentType;

    private String address;

    /**
     * Estado actual del reporte (PENDING, VERIFIED, REJECTED, RESOLVED).
     * NO estaba en el CreateDTO porque el sistema lo asigna automáticamente
     * (PENDING).
     * Aquí SÍ se devuelve para que el ciudadano vea el progreso de su reporte.
     */
    private ReportStatus status;

    /**
     * Fuente del reporte. Útil para estadísticas y filtros.
     */
    private ReportSource source;

    private Double latitude;

    private Double longitude;

    private String photoUrl;

    private Double trustScore;

    /**
     * Fecha de creación del reporte.
     * Corresponde al campo reportDate de la entidad, que usa @CreationTimestamp.
     *
     * @JsonFormat controla cómo se serializa a JSON:
     *             - SIN esta anotación: LocalDateTime se serializa como array
     *             [2026,3,15,11,24,16]
     *             - CON esta anotación: se serializa como "2026-03-15 11:24:16"
     *             (legible para humanos)
     *
     *             El pattern "yyyy-MM-dd HH:mm:ss" define el formato exacto:
     *             yyyy = año 4 dígitos, MM = mes, dd = día, HH = hora 24h, mm =
     *             minutos, ss = segundos
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportDate;

    // Zona asociada (puede ser null si el reporte no tiene zona)
    private Long zoneId;
    private String zoneName;
}
