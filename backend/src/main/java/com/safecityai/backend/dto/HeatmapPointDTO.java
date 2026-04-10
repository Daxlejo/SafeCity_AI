package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Punto para el mapa de calor (heatmap).
 * El frontend (leaflet.heat) necesita: [latitud, longitud, intensidad]
 * Intensidad va de 0.0 a 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPointDTO {

    private Double latitude;
    private Double longitude;
    private Double intensity;
}
