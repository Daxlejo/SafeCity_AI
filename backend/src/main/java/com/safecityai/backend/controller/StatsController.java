package com.safecityai.backend.controller;

import com.safecityai.backend.dto.HeatmapPointDTO;
import com.safecityai.backend.dto.StatsSummaryDTO;
import com.safecityai.backend.dto.TypeCountDTO;
import com.safecityai.backend.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    // GET /api/v1/stats/summary → resumen completo para el dashboard
    @GetMapping("/summary")
    public ResponseEntity<StatsSummaryDTO> getSummary() {
        return ResponseEntity.ok(statsService.getSummary());
    }

    // GET /api/v1/stats/by-type → conteo por tipo de incidente
    @GetMapping("/by-type")
    public ResponseEntity<List<TypeCountDTO>> getByType() {
        return ResponseEntity.ok(statsService.getReportsByType());
    }

    // GET /api/v1/stats/heatmap → datos para el mapa de calor
    @GetMapping("/heatmap")
    public ResponseEntity<List<HeatmapPointDTO>> getHeatmap() {
        return ResponseEntity.ok(statsService.getHeatmapData());
    }
}
