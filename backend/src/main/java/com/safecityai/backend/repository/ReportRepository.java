package com.safecityai.backend.repository;

import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    // Reportes públicos: excluye rechazados
    Page<Report> findByStatusNot(ReportStatus status, Pageable pageable);

    // Conteo por tipo de incidente → para grafica de barras
    @Query("SELECT r.incidentType, COUNT(r) FROM Report r GROUP BY r.incidentType")
    List<Object[]> countByIncidentType();

    // Conteo por zona → para estadísticas por zona
    @Query("SELECT r.zoneId, COUNT(r) FROM Report r WHERE r.zoneId IS NOT NULL GROUP BY r.zoneId")
    List<Object[]> countByZoneId();

    // Conteo por status
    long countByStatus(ReportStatus status);

    // Reportes con coordenadas → para heatmap
    @Query("SELECT r FROM Report r WHERE r.latitude IS NOT NULL AND r.longitude IS NOT NULL")
    List<Report> findAllWithCoordinates();

    // Timeline: reportes cercanos a una zona (por area) ordenados por fecha
    @Query("SELECT r FROM Report r WHERE r.latitude BETWEEN :minLat AND :maxLat " +
           "AND r.longitude BETWEEN :minLng AND :maxLng ORDER BY r.reportDate DESC")
    List<Report> findByArea(double minLat, double maxLat, double minLng, double maxLng);

    // Reportes recientes (últimos N días) para ranking semanal
    @Query("SELECT r FROM Report r WHERE r.reportDate >= :since AND r.latitude IS NOT NULL")
    List<Report> findRecentWithCoordinates(java.time.LocalDateTime since);

    // OSINT V2: Deduplicación geoespacial+temporal (bounding box ≈ 500m radius)
    @Query("SELECT COUNT(r) > 0 FROM Report r " +
           "WHERE r.incidentType = :incidentType " +
           "AND r.reportDate >= :since " +
           "AND r.latitude BETWEEN :minLat AND :maxLat " +
           "AND r.longitude BETWEEN :minLng AND :maxLng")
    boolean existsNearbyDuplicate(
            @Param("incidentType") IncidentType incidentType,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("since") LocalDateTime since);

    // Reputación histórica del usuario: promedio de trust score de sus reportes verificados
    @Query("SELECT AVG(r.trustScore) FROM Report r WHERE r.reportedBy.id = :userId AND r.status = 'VERIFIED' AND r.trustScore IS NOT NULL")
    Double findAverageTrustScoreByUser(Long userId);
}
