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
 *
 * ¿Por qué un DTO y no usar la entidad Report directamente?
 * 1. Seguridad: el cliente NO debe poder setear id, status ni reportDate
 * 2. Validación: aquí ponemos las reglas de lo que ENTRA
 * 3. Desacoplamiento: si la entidad cambia, la API no se rompe
 *
 * Las anotaciones de validación se activan cuando el controller usa @Valid.
 */
@Data // Lombok: genera getters, setters, toString, equals, hashCode
@NoArgsConstructor // Constructor vacío (necesario para deserialización JSON)
@AllArgsConstructor // Constructor con todos los campos
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
     *          → Usamos @NotNull y NO @NotBlank porque @NotBlank solo funciona con
     *          Strings.
     *          Los enums son objetos, no Strings, así que @NotBlank daría error.
     *
     *          Jackson automáticamente convierte el String del JSON ("ROBBERY")
     *          al valor del enum (IncidentType.ROBBERY). Si el cliente manda un
     *          valor
     *          que no existe en el enum, Spring devuelve 400 automáticamente.
     */
    @NotNull(message = "El tipo de incidente es obligatorio")
    private IncidentType incidentType;

    /**
     * Dirección legible del incidente.
     *
     * Es obligatoria porque en la entidad Report tiene @Column(nullable = false).
     * Si tu DTO lo acepta como opcional pero la BD lo requiere, obtendrías
     * un error de BD en vez de un error de validación limpio (400 vs 500).
     *
     * Regla: las validaciones del DTO deben ser >= estrictas que las de la BD.
     */
    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
    private String address;

    /**
     * Medio por el cual se genera el reporte (CITIZEN_TEXT, CITIZEN_VOICE, etc.)
     *
     * También es un enum → usamos @NotNull (no @NotBlank).
     * En la entidad tiene @Column(nullable = false), así que es obligatorio.
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
}
