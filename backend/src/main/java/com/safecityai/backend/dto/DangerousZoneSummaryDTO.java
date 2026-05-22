package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la zona más peligrosa de la semana.
 * Contiene coordenadas centrales, radio estimado, conteo de reportes,
 * tipo de incidente principal y etiqueta descriptiva.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DangerousZoneSummaryDTO {

    // Coordenadas del centro de la celda
    private Double latitude;
    private Double longitude;

    // Radio estimado de la zona en metros (~100m por celda de 200m)
    private Double radiusMeters;

    // Cantidad de reportes en esta zona durante la ventana temporal
    private Long reportCount;

    // Tipo de incidente más frecuente en la zona
    private String topIncidentType;

    // Etiqueta descriptiva (e.g. "Zona Centro - Robo")
    private String label;
}
