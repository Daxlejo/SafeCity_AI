package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para operaciones geoespaciales con reportes.
 *
 * Endpoints disponibles:
 * - GET /reports/nearby  → Reportes cercanos a un punto (radio en km)
 * - GET /reports/zone    → Reportes dentro de una zona rectangular
 *
 * Todos los endpoints devuelven List<ReportResponseDTO> para no exponer
 * la entidad JPA directamente al cliente.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Reports - Geoespacial", description = "Consultas geoespaciales de reportes")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * GET /reports/nearby?lat=4.6097&lng=-74.0817&radius=5
     *
     * Busca reportes cercanos a un punto geográfico dentro de un radio.
     *
     * Ejemplo de uso desde el frontend:
     * - El usuario abre el mapa y su GPS marca lat=4.6097, lng=-74.0817 (Bogotá)
     * - Quiere ver reportes a máximo 5 km → radius=5
     * - El frontend hace: fetch("/api/reports/nearby?lat=4.6097&lng=-74.0817&radius=5")
     *
     * @param lat    Latitud del punto central (obligatorio)
     * @param lng    Longitud del punto central (obligatorio)
     * @param radius Radio de búsqueda en kilómetros (obligatorio)
     * @return 200 OK con lista de reportes, o 400 si parámetros inválidos
     */
    @GetMapping("/nearby")
    @Operation(
            summary = "Buscar reportes cercanos",
            description = "Encuentra reportes dentro de un radio (km) desde un punto usando la fórmula de Haversine"
    )
    public ResponseEntity<List<ReportResponseDTO>> findNearby(
            @Parameter(description = "Latitud del punto central", example = "4.6097")
            @RequestParam double lat,

            @Parameter(description = "Longitud del punto central", example = "-74.0817")
            @RequestParam double lng,

            @Parameter(description = "Radio de búsqueda en km", example = "5")
            @RequestParam double radius
    ) {
        List<ReportResponseDTO> reports = reportService.findNearbyReports(lat, lng, radius);
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /reports/zone?latMin=4.55&latMax=4.65&lngMin=-74.12&lngMax=-74.05
     *
     * Busca reportes dentro de un rectángulo geográfico (bounding box).
     *
     * Ejemplo de uso desde el frontend:
     * - El usuario arrastra el mapa para ver una zona específica
     * - El frontend calcula las coordenadas de las esquinas visibles
     * - Hace: fetch("/api/reports/zone?latMin=4.55&latMax=4.65&lngMin=-74.12&lngMax=-74.05")
     *
     * @param latMin Latitud mínima - borde sur (obligatorio)
     * @param latMax Latitud máxima - borde norte (obligatorio)
     * @param lngMin Longitud mínima - borde oeste (obligatorio)
     * @param lngMax Longitud máxima - borde este (obligatorio)
     * @return 200 OK con lista de reportes, o 400 si parámetros inválidos
     */
    @GetMapping("/zone")
    @Operation(
            summary = "Buscar reportes por zona",
            description = "Encuentra reportes dentro de un rectángulo geográfico (bounding box)"
    )
    public ResponseEntity<List<ReportResponseDTO>> findByZone(
            @Parameter(description = "Latitud mínima (sur)", example = "4.55")
            @RequestParam double latMin,

            @Parameter(description = "Latitud máxima (norte)", example = "4.65")
            @RequestParam double latMax,

            @Parameter(description = "Longitud mínima (oeste)", example = "-74.12")
            @RequestParam double lngMin,

            @Parameter(description = "Longitud máxima (este)", example = "-74.05")
            @RequestParam double lngMax
    ) {
        List<ReportResponseDTO> reports = reportService.findReportsByZone(latMin, latMax, lngMin, lngMax);
        return ResponseEntity.ok(reports);
    }
}
