package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.RiskLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdated;
}
