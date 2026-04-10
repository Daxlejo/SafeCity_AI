package com.safecityai.backend.controller;

import com.safecityai.backend.dto.StatsSummaryDTO;
import com.safecityai.backend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    // GET /api/v1/stats/summary → resumen completo (público, para el Dashboard)
    @GetMapping("/summary")
    public ResponseEntity<StatsSummaryDTO> getSummary() {
        return ResponseEntity.ok(statsService.getSummary());
    }

    // GET /api/v1/stats/by-type → conteo por tipo de incidente
    @GetMapping("/by-type")
    public ResponseEntity<Map<String, Long>> getByType() {
        return ResponseEntity.ok(statsService.getByType());
    }

    // GET /api/v1/stats/by-zone → conteo por zona
    @GetMapping("/by-zone")
    public ResponseEntity<Map<String, Long>> getByZone() {
        return ResponseEntity.ok(statsService.getByZone());
    }

    // GET /api/v1/stats/timeline → tendencia últimos 7 días
    @GetMapping("/timeline")
    public ResponseEntity<List<Map<String, Object>>> getTimeline() {
        return ResponseEntity.ok(statsService.getTimeline());
    }
}
