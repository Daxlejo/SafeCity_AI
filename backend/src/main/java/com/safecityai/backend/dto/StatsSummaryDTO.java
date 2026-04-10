package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsSummaryDTO {

    // Resumen general
    private Long totalReports;
    private Long totalZones;
    private Long totalUsers;

    // Conteo por tipo de incidente: {"ROBBERY": 15, "ACCIDENT": 8, ...}
    private Map<String, Long> byType;

    // Conteo por zona: {"Centro Histórico": 20, "Zona Norte": 5, ...}
    private Map<String, Long> byZone;

    // Tendencia semanal: [{"date": "2026-04-01", "count": 5}, ...]
    private List<Map<String, Object>> timeline;
}
