package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.RiskLevel;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoneCreateDTO {

    @NotBlank(message = "El nombre de la zona es obligatorio")
    @Size(max = 100)
    private String name;

    // Coordenadas del centro de la zona
    @NotNull(message = "La latitud es obligatoria")
    private Double centerLat;

    @NotNull(message = "La longitud es obligatoria")
    private Double centerLng;

    // Radio en metros (por defecto 500m si no se envia)
    private Double radius;

    // Nivel de riesgo (si no se envia, se calcula automaticamente)
    private RiskLevel riskLevel;
}
