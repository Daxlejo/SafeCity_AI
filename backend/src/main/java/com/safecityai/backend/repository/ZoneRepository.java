package com.safecityai.backend.repository;

import com.safecityai.backend.model.Zone;
import com.safecityai.backend.model.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    // Spring traduce esto a: SELECT * FROM zones WHERE risk_level = ?
    List<Zone> findByRiskLevel(RiskLevel riskLevel);

    // SELECT * FROM zones WHERE risk_level IN ('HIGH', 'CRITICAL')
    List<Zone> findByRiskLevelIn(List<RiskLevel> levels);

    // SELECT * FROM zones ORDER BY report_count DESC
    List<Zone> findAllByOrderByReportCountDesc();
}
