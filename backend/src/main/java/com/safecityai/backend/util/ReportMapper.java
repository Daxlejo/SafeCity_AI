package com.safecityai.backend.util;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import org.springframework.stereotype.Component;

@Component
public class ReportMapper {

    private final PhotoUrlHelper photoUrlHelper;

    public ReportMapper(PhotoUrlHelper photoUrlHelper) {
        this.photoUrlHelper = photoUrlHelper;
    }

    public ReportResponseDTO convertToDTO(Report report) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .description(report.getDescription())
                .incidentType(report.getIncidentType())
                .address(report.getAddress())
                .status(report.getStatus())
                .source(report.getSource())
                .latitude(report.getLatitude())
                .longitude(report.getLongitude())
                .photoUrl(photoUrlHelper.getFullPhotoUrl(report.getPhotoUrl()))
                .trustScore(report.getTrustScore())
                .aiAnalysis(report.getAiAnalysis())
                .zoneId(report.getZoneId())
                .reportDate(report.getReportDate())
                .incidentDate(report.getIncidentDate())
                .build();
    }
}
