package com.safecityai.backend.model;

import com.safecityai.backend.model.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel = RiskLevel.LOW;

    // Centro de la zona (coordenadas)
    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    // Radio en metros 
    @Builder.Default
    @Column(nullable = false)
    private Double radius = 500.0;

    // Cantidad de reportes en esta zona
    @Builder.Default
    @Column(name = "report_count")
    private Integer reportCount = 0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
