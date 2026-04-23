package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DangerousZoneDTO {

    // Coordenadas representadas como "lat, lng" (aproximadas al área)
    private String areaName;
    // Cantidad de incidentes en los últimos N días
    private long incidentCount;
    // Tipo de incidente más frecuente en esa zona
    private String mostCommonType;
    private String riskLevel;
    private int rank;
}
