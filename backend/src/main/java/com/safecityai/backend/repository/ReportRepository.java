package com.safecityai.backend.repository;

import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    // Conteo por tipo de incidente → para grafica de barras
    @Query("SELECT r.incidentType, COUNT(r) FROM Report r GROUP BY r.incidentType")
    List<Object[]> countByIncidentType();

    // Conteo por status
    long countByStatus(ReportStatus status);

    // Reportes con coordenadas → para heatmap
    @Query("SELECT r FROM Report r WHERE r.latitude IS NOT NULL AND r.longitude IS NOT NULL")
    List<Report> findAllWithCoordinates();

    // Timeline: reportes cercanos a una zona (por area) ordenados por fecha
    @Query("SELECT r FROM Report r WHERE r.latitude BETWEEN :minLat AND :maxLat " +
           "AND r.longitude BETWEEN :minLng AND :maxLng ORDER BY r.reportDate DESC")
    List<Report> findByArea(double minLat, double maxLat, double minLng, double maxLng);
}
