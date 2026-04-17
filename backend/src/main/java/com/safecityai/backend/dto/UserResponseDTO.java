package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private Long id;
    private String name;
    private String email;
    private String cedula;
    private UserRole role;
    private Double trustLevel;
    private Boolean active;
    private LocalDateTime createdAt;
}
