package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneResponseDTO {

    private Long id;
    private String name;
    private RiskLevel riskLevel;
    private Double centerLat;
    private Double centerLng;
    private Double radius;
    private Integer reportCount;
    private LocalDateTime lastUpdated;
}
