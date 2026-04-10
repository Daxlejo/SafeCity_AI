package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado del analisis de la IA sobre un reporte.
 * Incluye clasificacion automatica, score y recomendacion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IAClassificationDTO {

    private Long reportId;

    // Tipo de incidente detectado por la IA (puede diferir del que puso el usuario)
    private IncidentType suggestedType;

    // Score de confianza de 0 a 100
    private Double trustScore;

    // Nivel de confianza categorizado
    private TrustLevel trustLevel;

    // Explicacion de la IA sobre por que clasifico asi
    private String reasoning;

    // Si la IA recomienda verificar (true) o rechazar (false)
    private Boolean shouldVerify;
}
