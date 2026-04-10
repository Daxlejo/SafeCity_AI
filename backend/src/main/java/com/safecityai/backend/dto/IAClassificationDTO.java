package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
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
    // Explicacion de la IA sobre por que clasifico asi
    private String reasoning;
    // Si la IA recomienda verificar (true) o rechazar (false)
    private Boolean shouldVerify;
}
