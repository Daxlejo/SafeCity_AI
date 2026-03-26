package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para operaciones con reportes.
 * 
 * Fusionado: CRUD completo + búsquedas geoespaciales.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Operaciones de CRUD y consultas geoespaciales de reportes")
public class ReportController {

    private final ReportService reportService;

    // ─────────────── CRUD BÁSICO ───────────────

    @PostMapping
    @Operation(summary = "Crear reporte", description = "Crea un nuevo reporte y notifica via WebSocket")
    public ResponseEntity<ReportResponseDTO> createReport(
            @Valid @RequestBody ReportCreateDTO dto) {
        ReportResponseDTO response = reportService.createReport(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener por ID")
    public ResponseEntity<ReportResponseDTO> getReportById(@PathVariable Long id) {
        ReportResponseDTO response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Listar todos (paginado)")
    public ResponseEntity<Page<ReportResponseDTO>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "reportDate") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<ReportResponseDTO> reports = reportService.getAllReports(pageable);
        return ResponseEntity.ok(reports);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar reporte")
    public ResponseEntity<ReportResponseDTO> updateReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportCreateDTO dto) {
        ReportResponseDTO response = reportService.updateReport(id, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar reporte")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────── GEOESPACIAL ───────────────

    @GetMapping("/nearby")
    @Operation(summary = "Buscar cercanos (Haversine)")
    public ResponseEntity<List<ReportResponseDTO>> findNearby(
            @Parameter(description = "Latitud", example = "4.6097") @RequestParam("lat") double lat,
            @Parameter(description = "Longitud", example = "-74.0817") @RequestParam("lng") double lng,
            @Parameter(description = "Radio en km", example = "5") @RequestParam("radius") double radius
    ) {
        List<ReportResponseDTO> reports = reportService.findNearbyReports(lat, lng, radius);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/zone")
    @Operation(summary = "Buscar por zona (Bounding Box)")
    public ResponseEntity<List<ReportResponseDTO>> findByZone(
            @Parameter(description = "Latitud min (sur)") @RequestParam("latMin") double latMin,
            @Parameter(description = "Latitud max (norte)") @RequestParam("latMax") double latMax,
            @Parameter(description = "Longitud min (oeste)") @RequestParam("lngMin") double lngMin,
            @Parameter(description = "Longitud max (este)") @RequestParam("lngMax") double lngMax
    ) {
        List<ReportResponseDTO> reports = reportService.findReportsByZone(latMin, latMax, lngMin, lngMax);
        return ResponseEntity.ok(reports);
    }
}
