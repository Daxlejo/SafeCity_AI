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
 * 1. INCLUYE id
 * 2. INCLUYE status
 * 3. INCLUYE reportDate
 * 4. NO tiene anotaciones de validación - no validamos lo que SALE, solo lo que
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponseDTO {

    private Long id;

    private String description;

    private IncidentType incidentType;

    private String address;

    private ReportStatus status;
    private ReportSource source;

    private Double latitude;

    private Double longitude;

    private String photoUrl;

    private Double trustScore;

    // Razonamiento de la IA (por qué clasificó así)
    private String aiAnalysis;

    /**
     * Fecha de creación del reporte.
     * Corresponde al campo reportDate de la entidad, que usa @CreationTimestamp.
     *
     * @JsonFormat controla cómo se serializa a JSON:
     *             - SIN esta anotación: LocalDateTime se serializa como array
     *             [2026,3,15,11,24,16]
     *             - CON esta anotación: se serializa como "2026-03-15 11:24:16"
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
