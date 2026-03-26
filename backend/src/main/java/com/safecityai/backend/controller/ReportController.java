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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para gestión de reportes ciudadanos.
 *
 * Este controller NO tiene lógica de negocio. Su responsabilidad es:
 * 1. Recibir la petición HTTP
 * 2. Delegar al ReportService
 * 3. Devolver la respuesta HTTP con el código correcto
 *
 * @RequestMapping("/api/v1/reports"):
 * - "/api" → prefijo estándar para APIs REST
 * - "/v1" → versionamiento de API (buena práctica para proyecto de grado)
 * - "/reports" → el recurso que maneja este controller
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ══════════════════════ POST ══════════════════════

    /**
     * POST /api/v1/reports
     * Crea un nuevo reporte ciudadano.
     *
     * @Valid → activa las validaciones del ReportCreateDTO (@NotBlank, @Size, etc.)
     *        Si alguna falla, Spring lanza MethodArgumentNotValidException
     *        que el GlobalExceptionHandler captura y devuelve un 400.
     *
     * @RequestBody → le dice a Spring que convierta el JSON del body a
     *              ReportCreateDTO.
     *
     *              HttpStatus.CREATED (201) → código HTTP correcto para "recurso
     *              creado".
     *              NO usamos 200 (OK) porque 201 es más preciso semánticamente.
     */
    @PostMapping
    public ResponseEntity<ReportResponseDTO> createReport(
            @Valid @RequestBody ReportCreateDTO dto) {

        ReportResponseDTO response = reportService.createReport(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ══════════════════════ GET BY ID ══════════════════════

    /**
     * GET /api/v1/reports/{id}
     * Obtiene un reporte específico por su ID.
     *
     * @PathVariable → extrae el {id} de la URL.
     *               Ejemplo: GET /api/v1/reports/5 → id = 5
     *
     *               Si el ID no existe, el service lanza ResourceNotFoundException
     *               → el GlobalExceptionHandler devuelve 404.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponseDTO> getReportById(@PathVariable Long id) {
        ReportResponseDTO response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    // ══════════════════════ GET ALL (PAGINADO) ══════════════════════

    /**
     * GET /api/v1/reports?page=0&size=10&sort=reportDate,desc
     * Lista reportes con PAGINACIÓN.
     *
     * ¿Por qué paginación?
     * Sin paginación, si hay 10,000 reportes en Pasto, el endpoint devolvería
     * un JSON de varios MB. Con paginación, el frontend pide de a "páginas".
     *
     * @RequestParam → parámetros opcionales de la URL (query params):
     *               - page: número de página (0-indexed). Default: 0 (primera
     *               página)
     *               - size: cantidad de reportes por página. Default: 20
     *               - sort: campo por el cual ordenar. Default: reportDate
     *               - direction: ASC o DESC. Default: DESC (más recientes primero)
     *
     *               Ejemplos de uso desde el frontend:
     *               GET /api/v1/reports → página 0, 20 reportes, más recientes
     *               GET /api/v1/reports?page=2&size=5 → página 2, 5 por página
     *               GET /api/v1/reports?sort=incidentType&direction=ASC → ordenados
     *               por tipo A-Z
     *
     *               La respuesta Page<> incluye metadata útil para el frontend:
     *               {
     *               "content": [...], ← los reportes de esta página
     *               "totalElements": 150, ← total de reportes en la BD
     *               "totalPages": 8, ← total de páginas
     *               "number": 0, ← página actual
     *               "size": 20, ← tamaño de página
     *               "first": true, ← ¿es la primera página?
     *               "last": false ← ¿es la última página?
     *               }
     */
    @GetMapping
    public ResponseEntity<Page<ReportResponseDTO>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "reportDate") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {

        // Sort.Direction.fromString convierte "ASC"/"DESC" (String) al enum
        // Sort.Direction
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);

        // PageRequest.of() crea el objeto Pageable que el Service necesita
        // Combina: número de página + tamaño + criterio de ordenamiento
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<ReportResponseDTO> reports = reportService.getAllReports(pageable);
        return ResponseEntity.ok(reports);
    }

    // ══════════════════════ PUT ══════════════════════

    /**
     * PUT /api/v1/reports/{id}
     * Actualiza un reporte existente.
     *
     * Combina @PathVariable (para el ID de la URL) con @RequestBody (para los datos
     * nuevos).
     * 
     * @Valid aplica las validaciones del DTO también en la actualización.
     *
     *        Retorna 200 (OK) con el reporte actualizado.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReportResponseDTO> updateReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportCreateDTO dto) {

        ReportResponseDTO response = reportService.updateReport(id, dto);
        return ResponseEntity.ok(response);
    }

    // ══════════════════════ DELETE ══════════════════════

    /**
     * DELETE /api/v1/reports/{id}
     * Elimina un reporte del sistema.
     *
     * ResponseEntity.noContent() → devuelve HTTP 204 (No Content).
     * 204 es el código correcto para "eliminé el recurso y no hay nada que
     * devolver".
     * NO usamos 200 porque no estamos devolviendo un body.
     *
     * .build() → construye la respuesta sin body (vacía).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
