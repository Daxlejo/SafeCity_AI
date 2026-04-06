package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertPreferenceDTO {

    private Long id;

    @NotNull(message = "El tipo de incidente es obligatorio")
    private IncidentType incidentType;

    private Boolean enabled = true;
}
