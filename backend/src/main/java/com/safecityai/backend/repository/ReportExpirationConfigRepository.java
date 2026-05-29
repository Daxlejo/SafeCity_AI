package com.safecityai.backend.repository;

import com.safecityai.backend.model.ReportExpirationConfig;
import com.safecityai.backend.model.enums.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportExpirationConfigRepository extends JpaRepository<ReportExpirationConfig, Long> {
    Optional<ReportExpirationConfig> findByIncidentType(IncidentType incidentType);
}
