package com.safecityai.backend.controller;

import com.safecityai.backend.dto.DangerousZoneDTO;
import com.safecityai.backend.dto.HeatmapPointDTO;
import com.safecityai.backend.dto.StatsSummaryDTO;
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
    public ResponseEntity<java.util.Map<String, Long>> getByType() {
        return ResponseEntity.ok(statsService.getReportsByType());
    }

    // GET /api/v1/stats/heatmap → datos para el mapa de calor
    @GetMapping("/heatmap")
    public ResponseEntity<List<HeatmapPointDTO>> getHeatmap() {
        return ResponseEntity.ok(statsService.getHeatmapData());
    }

    // GET /api/v1/stats/by-zone → conteo por zona
    @GetMapping("/by-zone")
    public ResponseEntity<java.util.Map<String, Long>> getByZone() {
        return ResponseEntity.ok(statsService.getReportsByZoneNames());
    }

    // GET /api/v1/stats/timeline → ultimos 7 reportes por dia
    @GetMapping("/timeline")
    public ResponseEntity<List<java.util.Map<String, Object>>> getTimeline() {
        return ResponseEntity.ok(statsService.getTimeline());
    }

    // GET /api/v1/stats/dangerous-zones?days=7&limit=10 → ranking de zonas peligrosas
    @GetMapping("/dangerous-zones")
    public ResponseEntity<List<DangerousZoneDTO>> getDangerousZones(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(statsService.getDangerousZones(days, limit));
    }
}
