package com.safecityai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginDTO {

    @NotBlank(message = "El usuario es obligatorio")
    private String identifier;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
