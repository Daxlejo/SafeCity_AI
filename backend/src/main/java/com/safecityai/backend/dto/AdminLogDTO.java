package com.safecityai.backend.dto;

import com.safecityai.backend.model.enums.AdminAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para los registros de auditoría administrativa.
 * Se usa en el endpoint de consulta de logs para el panel de administración.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLogDTO {

    private Long id;
    private Long adminId;
    private String adminEmail;
    private AdminAction action;
    private String targetType;
    private String targetId;
    private String details;
    private LocalDateTime timestamp;
}
