package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IAClassificationDTO {

    private Long reportId;
    private IncidentType suggestedType;
    private Double trustScore;
    private TrustLevel trustLevel;
    private String reasoning;

    /**
     * AI-determined status: PENDING, REJECTED, or VERIFIED.
     * Replaces the old shouldVerify boolean logic.
     */
    private ReportStatus statusDecision;

    /**
     * @deprecated Kept for backward compatibility with IAController.
     * Derived from statusDecision == VERIFIED.
     */
    @Deprecated
    private Boolean shouldVerify;
}
