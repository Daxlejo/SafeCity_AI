package com.safecityai.backend.service;

import com.safecityai.backend.dto.AdminLogDTO;
import com.safecityai.backend.model.AdminLog;
import com.safecityai.backend.model.enums.AdminAction;
import com.safecityai.backend.repository.AdminLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de auditoría para registrar todas las acciones sensibles
 * realizadas por administradores en la plataforma SafeCity AI.
 *
 * Principio SRP: este servicio SOLO se encarga de crear y consultar logs.
 * Los controllers/services que realizan acciones administrativas llaman
 * a {@link #log} después de ejecutar la operación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogRepository adminLogRepository;

    /**
     * Registra una acción administrativa en el log de auditoría.
     *
     * @param adminId    ID del administrador que realizó la acción
     * @param adminEmail Email del administrador
     * @param action     Tipo de acción (del enum AdminAction)
     * @param targetType Tipo de entidad afectada (ej: "Report", "User")
     * @param targetId   ID de la entidad afectada
     * @param details    Detalles adicionales en formato libre
     */
    @Transactional
    public void log(Long adminId, String adminEmail, AdminAction action,
                    String targetType, String targetId, String details) {

        AdminLog entry = AdminLog.builder()
                .adminId(adminId)
                .adminEmail(adminEmail)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build();

        adminLogRepository.save(entry);
        log.info("Audit Log: [{}] {} → {} (id: {}) | {}",
                adminEmail, action, targetType, targetId, details);
    }

    /** Obtiene todos los logs paginados (más recientes primero) */
    @Transactional(readOnly = true)
    public Page<AdminLogDTO> getAllLogs(Pageable pageable) {
        return adminLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toDTO);
    }

    /** Obtiene logs filtrados por administrador */
    @Transactional(readOnly = true)
    public Page<AdminLogDTO> getLogsByAdmin(Long adminId, Pageable pageable) {
        return adminLogRepository.findByAdminIdOrderByTimestampDesc(adminId, pageable)
                .map(this::toDTO);
    }

    /** Obtiene logs filtrados por tipo de acción */
    @Transactional(readOnly = true)
    public Page<AdminLogDTO> getLogsByAction(AdminAction action, Pageable pageable) {
        return adminLogRepository.findByActionOrderByTimestampDesc(action, pageable)
                .map(this::toDTO);
    }

    // ═══════════════ HELPER ═══════════════
    private AdminLogDTO toDTO(AdminLog entity) {
        return AdminLogDTO.builder()
                .id(entity.getId())
                .adminId(entity.getAdminId())
                .adminEmail(entity.getAdminEmail())
                .action(entity.getAction())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .details(entity.getDetails())
                .timestamp(entity.getTimestamp())
                .build();
    }
}
