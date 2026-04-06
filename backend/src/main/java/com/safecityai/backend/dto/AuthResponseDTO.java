package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {

    private String token;
    private String type;
    private UserResponseDTO user;

    public static AuthResponseDTO of(String token, UserResponseDTO user) {
        return AuthResponseDTO.builder()
                .token(token)
                .type("Bearer")
                .user(user)
                .build();
    }
}
