package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Operaciones CRUD para los incidentes/reportes de seguridad ciudadana")
public class ReportController {

    private final ReportService reportService;

    // POST /api/v1/reports → 201 Created
    @PostMapping
    @Operation(summary = "Crear un nuevo reporte", description = "Crea un incidente. El sistema opcionalmente detectará la dirección con geocoding y el reporte quedará pendiente de verificación por la IA.")
    @ApiResponse(responseCode = "201", description = "Reporte creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (DTO malformado)")
    public ResponseEntity<ReportResponseDTO> createReport(
            @Valid @RequestBody ReportCreateDTO dto,
            Authentication auth) {

        String userEmail = auth != null ? auth.getName() : null;
        ReportResponseDTO response = reportService.createReport(dto, userEmail);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/v1/reports/{id} → 200 OK | 404 Not Found
    @GetMapping("/{id}")
    @Operation(summary = "Obtener reporte por ID", description = "Obtiene los detalles completos de un reporte específico, incluyendo resultados del análisis de la IA y trust score.")
    @ApiResponse(responseCode = "200", description = "Reporte encontrado")
    @ApiResponse(responseCode = "404", description = "Reporte no encontrado")
    public ResponseEntity<ReportResponseDTO> getReportById(@PathVariable Long id) {
        ReportResponseDTO response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/reports?page=0&size=20&sort=reportDate&direction=DESC
    @GetMapping
    @Operation(summary = "Listar reportes paginados", description = "Retorna una página de reportes ordenados. No requiere autenticación para que el mapa pueda consumirlos libremente.")
    @ApiResponse(responseCode = "200", description = "Página de reportes obtenida")
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

    // PUT /api/v1/reports/{id} → 200 OK | 404 Not Found
    @PutMapping("/{id}")
    @Operation(summary = "Actualizar reporte", description = "Actualiza la información manual de un reporte (descripción, coordenadas). Requiere re-validación/moderación si cambia la descripción.")
    @ApiResponse(responseCode = "200", description = "Reporte actualizado")
    @ApiResponse(responseCode = "404", description = "Reporte no encontrado")
    public ResponseEntity<ReportResponseDTO> updateReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportCreateDTO dto) {

        ReportResponseDTO response = reportService.updateReport(id, dto);
        return ResponseEntity.ok(response);
    }

    // DELETE /api/v1/reports/{id} → 204 No Content | 404 Not Found
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar reporte", description = "Elimina de manera permanente un reporte. Esta acción emitirá un evento en WebSocket para removerlo de los clientes activos.")
    @ApiResponse(responseCode = "204", description = "Reporte eliminado exitosamente")
    @ApiResponse(responseCode = "404", description = "Reporte no encontrado")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
