package com.safecityai.backend.repository;

import com.safecityai.backend.model.AlertPreference;
import com.safecityai.backend.model.enums.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertPreferenceRepository extends JpaRepository<AlertPreference, Long> {

    List<AlertPreference> findByUserId(Long userId);

    List<AlertPreference> findByUserIdAndEnabledTrue(Long userId);

    Optional<AlertPreference> findByUserIdAndIncidentType(Long userId, IncidentType incidentType);

    void deleteByUserIdAndIncidentType(Long userId, IncidentType incidentType);
}
