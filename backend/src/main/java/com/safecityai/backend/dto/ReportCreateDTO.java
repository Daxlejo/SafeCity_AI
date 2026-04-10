package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para CREAR un nuevo reporte.
 * Solo contiene los campos que el CIUDADANO debe enviar.
 * Campos como id, status y reportDate los genera el sistema automáticamente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportCreateDTO {

    // ──────────── CAMPOS OBLIGATORIOS ────────────
    /**
     * Descripción detallada de lo que ocurrió.
     *
     * @NotBlank = no puede ser null, ni vacío "", ni solo espacios " "
     *           → Se usa para Strings. Para enums o números usamos @NotNull.
     *
     * @Size = limita la longitud mínima y máxima del texto.
     *       → min=10 para forzar descripciones útiles, max=500 para coincidir
     *       con el @Column(length = 500) de la entidad Report.
     */
    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, max = 500, message = "La descripción debe tener entre 10 y 500 caracteres")
    private String description;

    /**
     * Tipo de incidente (ROBBERY, ASSAULT, THEFT, etc.)
     *
     * @NotNull = no puede ser null.
     */
    @NotNull(message = "El tipo de incidente es obligatorio")
    private IncidentType incidentType;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String address;

    /**
     * Medio por el cual se genera el reporte (CITIZEN_TEXT, CITIZEN_VOICE, etc.)
     */
    @NotNull(message = "La fuente del reporte es obligatoria")
    private ReportSource source;

    /**
     * Coordenadas geográficas del incidente.
     *
     * Son opcionales porque en la entidad Report NO tienen @Column(nullable =
     * false).
     * El ciudadano puede no tener GPS activado o no querer compartir ubicación
     * exacta.
     *
     * Usamos Double (objeto wrapper) en vez de double (primitivo) porque:
     * - double primitivo siempre tiene valor (0.0 por defecto)
     * - Double objeto puede ser null → podemos distinguir "no envió" vs "envió 0.0"
     *
     * @DecimalMin/@DecimalMax = limitan el rango numérico válido.
     *                         → Latitud válida: -90.0 a 90.0
     *                         → Longitud válida: -180.0 a 180.0
     *                         → Estas validaciones SOLO se aplican si el valor NO
     *                         es null.
     */
    @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
    @DecimalMax(value = "90.0", message = "La latitud debe ser menor o igual a 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
    @DecimalMax(value = "180.0", message = "La longitud debe ser menor o igual a 180")
    private Double longitude;

    // URL de la foto del incidente (opcional, sube el trust score)
    private String photoUrl;

    // Zona del incidente
    private Long zoneId;
}
