package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsSummaryDTO {

    // Totales para las tarjetas del dashboard
    private Long totalReports;
    private Long pendingReports;
    private Long verifiedReports;
    private Long rejectedReports;
    private Long totalUsers;
    private Long totalZones;

    // Conteo por tipo de incidente (para la grafica de barras)
    private List<TypeCountDTO> reportsByType;

    // Datos para el heatmap: lista de puntos [lat, lng, intensidad]
    private List<HeatmapPointDTO> heatmapData;
}
