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

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // POST /api/v1/reports → 201 Created
    @PostMapping
    public ResponseEntity<ReportResponseDTO> createReport(
            @Valid @RequestBody ReportCreateDTO dto) {

        ReportResponseDTO response = reportService.createReport(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // GET /api/v1/reports/{id} → 200 OK | 404 Not Found
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponseDTO> getReportById(@PathVariable Long id) {
        ReportResponseDTO response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    // GET /api/v1/reports?page=0&size=20&sort=reportDate&direction=DESC
    @GetMapping
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
    public ResponseEntity<ReportResponseDTO> updateReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportCreateDTO dto) {

        ReportResponseDTO response = reportService.updateReport(id, dto);
        return ResponseEntity.ok(response);
    }

    // DELETE /api/v1/reports/{id} → 204 No Content | 404 Not Found
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }
}
