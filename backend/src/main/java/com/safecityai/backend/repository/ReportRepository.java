package com.safecityai.backend.repository;

import com.safecityai.backend.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * Busca reportes cercanos a un punto usando la fórmula de Haversine.
     *
     * Haversine calcula la distancia entre dos puntos sobre una esfera (la Tierra).
     * 6371 = radio de la Tierra en km.
     * RADIANS() convierte grados → radianes (necesario para funciones trigonométricas).
     *
     * La fórmula completa:
     * distancia = 6371 × acos(
     *     cos(rad(lat1)) × cos(rad(lat2)) × cos(rad(lng2) - rad(lng1))
     *   + sin(rad(lat1)) × sin(rad(lat2))
     * )
     *
     * @param lat      Latitud del punto central de búsqueda
     * @param lng      Longitud del punto central de búsqueda
     * @param radiusKm Radio de búsqueda en kilómetros
     * @return Lista de reportes dentro del radio especificado
     */
    @Query(value = """
            SELECT * FROM reports r
            WHERE r.latitude IS NOT NULL
              AND r.longitude IS NOT NULL
              AND (6371 * ACOS(
                    COS(RADIANS(:lat)) * COS(RADIANS(r.latitude))
                    * COS(RADIANS(r.longitude) - RADIANS(:lng))
                    + SIN(RADIANS(:lat)) * SIN(RADIANS(r.latitude))
                  )) <= :radiusKm
            """, nativeQuery = true)
    List<Report> findNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm
    );

    /**
     * Busca reportes dentro de una zona rectangular (bounding box).
     *
     * Un bounding box se define con 4 valores:
     * - latMin / latMax → rango de latitudes (sur ↔ norte)
     * - lngMin / lngMax → rango de longitudes (oeste ↔ este)
     *
     * Es más rápido que Haversine porque solo hace comparaciones simples (>=, <=).
     * Ideal para: "muéstrame todos los reportes en esta zona del mapa".
     *
     * @param latMin Latitud mínima (borde sur)
     * @param latMax Latitud máxima (borde norte)
     * @param lngMin Longitud mínima (borde oeste)
     * @param lngMax Longitud máxima (borde este)
     * @return Lista de reportes dentro de la zona
     */
    @Query(value = """
            SELECT * FROM reports r
            WHERE r.latitude IS NOT NULL
              AND r.longitude IS NOT NULL
              AND r.latitude  >= :latMin
              AND r.latitude  <= :latMax
              AND r.longitude >= :lngMin
              AND r.longitude <= :lngMax
            """, nativeQuery = true)
    List<Report> findByZone(
            @Param("latMin") double latMin,
            @Param("latMax") double latMax,
            @Param("lngMin") double lngMin,
            @Param("lngMax") double lngMax
    );
}
