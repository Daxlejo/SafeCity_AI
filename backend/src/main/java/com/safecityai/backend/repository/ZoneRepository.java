package com.safecityai.backend.repository;

import com.safecityai.backend.model.Zone;
import com.safecityai.backend.model.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    // Buscar zonas por nivel de riesgo
    List<Zone> findByRiskLevel(RiskLevel riskLevel);

    // Buscar zonas de riesgo alto y crítico (para el mapa de calor)
    List<Zone> findByRiskLevelIn(List<RiskLevel> levels);

    // Buscar zonas cercanas a un punto dado (fórmula Haversine simplificada)
    @Query("SELECT z FROM Zone z WHERE " +
           "(6371000 * acos(cos(radians(:lat)) * cos(radians(z.centerLat)) * " +
           "cos(radians(z.centerLng) - radians(:lng)) + " +
           "sin(radians(:lat)) * sin(radians(z.centerLat)))) <= z.radius")
    List<Zone> findNearby(@Param("lat") Double lat, @Param("lng") Double lng);
}
