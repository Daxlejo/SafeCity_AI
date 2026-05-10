package com.safecityai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que expone al frontend la cuota de reportes del usuario autenticado.
 * Permite al perfil y al formulario de reporte mostrar información en tiempo real:
 * "Te quedan 2 de 3 reportes esta hora"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportQuotaDTO {

    /** Máximo de reportes permitidos en la hora actual según TrustLevel */
    private int limit;

    /** Reportes ya enviados en la hora actual */
    private int used;

    /** Reportes restantes (limit - used) */
    private int remaining;

    /** Timestamp ISO 8601 de cuándo se reinicia la ventana (inicio de la siguiente hora) */
    private String resetsAt;
}
