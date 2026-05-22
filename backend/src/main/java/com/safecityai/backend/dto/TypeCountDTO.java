package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.IncidentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypeCountDTO {

    private IncidentType type;
    private Long count;
}
