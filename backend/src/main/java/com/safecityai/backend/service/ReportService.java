package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.enums.ReportStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary MOCK for ReportService.
 * David will replace this with the final implementation using ReportRepository.
 */
@Service
public class ReportService {

    public ReportResponseDTO createReport(ReportCreateDTO dto) {
        return ReportResponseDTO.builder()
                .id(System.currentTimeMillis())
                .description(dto.getDescription())
                .incidentType(dto.getIncidentType())
                .address(dto.getAddress())
                .status(ReportStatus.PENDING)
                .source(dto.getSource())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .reportDate(LocalDateTime.now())
                .build();
    }

    public ReportResponseDTO getReportById(Long id) {
        return ReportResponseDTO.builder()
                .id(id)
                .description("Temporary mock report")
                .status(ReportStatus.PENDING)
                .reportDate(LocalDateTime.now())
                .build();
    }

    public List<ReportResponseDTO> getAllReports() {
        return new ArrayList<>(); // Empty for safety until DB is connected
    }

    public ReportResponseDTO updateReport(Long id, ReportCreateDTO dto) {
        return createReport(dto); // Reusing mock logic for now
    }

    public void deleteReport(Long id) {
        // Mock method
    }
}
