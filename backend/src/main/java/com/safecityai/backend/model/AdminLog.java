package com.safecityai.backend.model;

import com.safecityai.backend.model.enums.AdminAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad JPA para el registro de auditoría de acciones administrativas.
 * Cada fila representa una acción sensible realizada por un administrador.
 *
 * Diseñada para ser inmutable una vez creada (sin setters de negocio),
 * garantizando la integridad del log para auditorías y trazabilidad.
 */
@Entity
@Table(name = "admin_logs", indexes = {
        @Index(name = "idx_admin_log_admin_id", columnList = "admin_id"),
        @Index(name = "idx_admin_log_action", columnList = "action"),
        @Index(name = "idx_admin_log_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del administrador que realizó la acción */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** Email del administrador (desnormalizado para consultas rápidas) */
    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    /** Tipo de acción realizada */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminAction action;

    /** Tipo de entidad afectada (ej: "Report", "User", "OsintConfig") */
    @Column(name = "target_type", length = 50)
    private String targetType;

    /** ID de la entidad afectada */
    @Column(name = "target_id")
    private String targetId;

    /** Detalles adicionales en formato libre (ej: "Status: PENDING → VERIFIED") */
    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
