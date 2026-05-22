package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.ReportSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Resultado de scraping OSINT: un incidente encontrado
 * en noticias o redes sociales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsintResultDTO {

    private String title;
    private String content;
    private String sourceUrl;
    private ReportSource sourceType;
    private String detectedLocation;
    private Double latitude;
    private Double longitude;
    private LocalDateTime publishedAt;
    private Double confidence;
}
