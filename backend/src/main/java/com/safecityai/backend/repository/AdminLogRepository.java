package com.safecityai.backend.repository;

import com.safecityai.backend.model.AdminLog;
import com.safecityai.backend.model.enums.AdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para los registros de auditoría administrativa.
 * Provee métodos de consulta para filtrar logs por administrador y tipo de acción.
 */
@Repository
public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    /** Todos los logs de un administrador específico, ordenados por fecha */
    Page<AdminLog> findByAdminIdOrderByTimestampDesc(Long adminId, Pageable pageable);

    /** Todos los logs filtrados por tipo de acción */
    Page<AdminLog> findByActionOrderByTimestampDesc(AdminAction action, Pageable pageable);

    /** Todos los logs ordenados por fecha (más recientes primero) */
    Page<AdminLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
