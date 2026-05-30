package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps the structured JSON response from GPT-4o-mini during OSINT V2 entity extraction.
 * A single AI call extracts entities AND evaluates trustScore to optimize API usage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsintAIResultDTO {

    private boolean securityIncident;
    private IncidentType incidentType;
    private String exactAddress;
    private String cleanSummary;
    private String estimatedDate;
    private double trustScore;
    private boolean shouldVerify;
}
