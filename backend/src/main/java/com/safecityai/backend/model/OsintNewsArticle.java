package com.safecityai.backend.model;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores security-relevant news articles detected by OSINT that could NOT be geolocated.
 * These appear in the "Noticias Pasto" section instead of the map.
 */
@Entity
@Table(name = "osint_news_articles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsintNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false)
    private IncidentType incidentType;

    // TEXT en lugar de VARCHAR(500) para soportar URLs largas (Google News, etc.)
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ReportSource sourceType;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    @Column(name = "trust_score")
    private Double trustScore;

    @Column(name = "estimated_date")
    private LocalDateTime estimatedDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
