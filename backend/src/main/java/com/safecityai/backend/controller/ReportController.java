package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for handling citizen reports.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    // Dependency injection via constructor automatically handled by Lombok (@RequiredArgsConstructor)
    private final ReportService reportService;

    /**
     * POST /api/reports
     * Allows a citizen to create a new report.
     * Using @Valid triggers the validation annotations defined in ReportCreateDTO.
     */
    @PostMapping
    public ResponseEntity<ReportResponseDTO> createReport(@Valid @RequestBody ReportCreateDTO dto) {
        ReportResponseDTO response = reportService.createReport(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * GET /api/reports/{id}
     * Retrieves the details of a specific report.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponseDTO> getReportById(@PathVariable Long id) {
        ReportResponseDTO response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/reports
     * Lists all reports in the system.
     */
    @GetMapping
    public ResponseEntity<List<ReportResponseDTO>> getAllReports() {
        List<ReportResponseDTO> reports = reportService.getAllReports();
        return ResponseEntity.ok(reports);
    }

    /**
     * PUT /api/reports/{id}
     * Updates the information of an existing report.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReportResponseDTO> updateReport(@PathVariable Long id, @Valid @RequestBody ReportCreateDTO dto) {
        ReportResponseDTO response = reportService.updateReport(id, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/reports/{id}
     * Deletes a report from the system.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
