package com.safecityai.backend.repository;

import com.safecityai.backend.model.OsintConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para la configuración OSINT.
 * Operaciones principales: obtener la configuración singleton.
 */
@Repository
public interface OsintConfigRepository extends JpaRepository<OsintConfig, Long> {

    /**
     * Obtiene la primera (y única) fila de configuración.
     * El módulo OSINT utiliza un patrón singleton-row:
     * siempre existe exactamente una fila en la tabla.
     */
    Optional<OsintConfig> findFirstByOrderByIdAsc();
}
