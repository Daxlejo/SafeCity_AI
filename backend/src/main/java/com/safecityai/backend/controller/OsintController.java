package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.service.OsintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/osint")
@Tag(name = "OSINT", description = "Operaciones de búsqueda y escaneo de incidentes en fuentes abiertas (Redes Sociales, Noticias)")
public class OsintController {

    private final OsintService osintService;

    public OsintController(OsintService osintService) {
        this.osintService = osintService;
    }

    // GET /api/v1/osint/search?city=Pasto
    @GetMapping("/search")
    @Operation(summary = "Buscar incidentes (Preview)", description = "Busca incidentes en fuentes abiertas para una ciudad específica, sin clasificarlos ni guardarlos en la base de datos.")
    @ApiResponse(responseCode = "200", description = "Búsqueda completada exitosamente")
    public ResponseEntity<List<OsintResultDTO>> search(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.searchIncidents(city));
    }

    /**
     * POST /api/v1/osint/scan?city=Pasto
     * Flujo:
     * 1. Busca en Google News + Facebook (paginas prioritarias + general)
     * 2. Crea un reporte en la BD por cada resultado
     * 3. Clasifica cada reporte (async)
     */
    @PostMapping("/scan")
    @Operation(summary = "Escanear y clasificar incidentes", description = "Ejecuta un escaneo completo de fuentes abiertas para la ciudad, crea reportes para los incidentes encontrados, y lanza el proceso de clasificación por IA de manera asíncrona.")
    @ApiResponse(responseCode = "200", description = "Pipeline de escaneo ejecutado correctamente (devuelve resumen del proceso)")
    public ResponseEntity<Map<String, Object>> scanAndClassify(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.scanAndClassify(city));
    }
}
