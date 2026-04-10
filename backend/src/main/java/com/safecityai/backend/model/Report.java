package com.safecityai.backend.model;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType incidentType;

    @Column(nullable = false)
    private String address;

    @CreationTimestamp
    @Column(name = "report_date", updatable = false)
    private LocalDateTime reportDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportSource source;

    private Double latitude;

    private Double longitude;

    // URL de la foto del incidente (sube el trust score)
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    // Zona a la que pertenece el reporte (para compatibilidad con tests frontend/postman)
    @Column(name = "zone_id", insertable = false, updatable = false)
    private Long zoneId;

    // Trust score calculado por la IA (0-100)
    @Column(name = "trust_score")
    private Double trustScore;

    // Relacion: cada reporte pertenece a un usuario
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User reportedBy;

    // Relación opcional: cada reporte puede pertenecer a una zona geográfica
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;
}
