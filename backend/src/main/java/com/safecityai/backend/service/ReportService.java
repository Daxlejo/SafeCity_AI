package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio que encapsula la lógica de negocio para reportes.
 *
 * ¿Por qué un Service separado del Controller?
 * 1. El Controller solo debe recibir HTTP y delegar. NO debe tener lógica.
 * 2. El Service contiene la lógica de negocio (validaciones, transformaciones).
 * 3. Así podemos testear la lógica SIN levantar un servidor HTTP.
 * 4. Si mañana agregamos seguridad (@PreAuthorize), va en el Service.
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;

    /**
     * Inyección de dependencia por constructor (recomendado sobre @Autowired en campo).
     * Spring automáticamente inyecta el ReportRepository aquí.
     */
    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Busca reportes cercanos a un punto geográfico.
     *
     * Ejemplo: encontrar todos los robos reportados a menos de 2km
     * de la ubicación actual del usuario.
     *
     * @param lat      Latitud del punto central
     * @param lng      Longitud del punto central
     * @param radiusKm Radio de búsqueda en kilómetros
     * @return Lista de ReportResponseDTO con los reportes encontrados
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    public List<ReportResponseDTO> findNearbyReports(double lat, double lng, double radiusKm) {
        // Validar que las coordenadas estén en rangos válidos
        validateCoordinates(lat, lng);

        if (radiusKm <= 0) {
            throw new IllegalArgumentException("El radio debe ser mayor a 0 km");
        }

        List<Report> reports = reportRepository.findNearby(lat, lng, radiusKm);
        return reports.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca reportes dentro de una zona rectangular (bounding box).
     *
     * Ejemplo: cuando el usuario mueve el mapa, el frontend envía
     * las coordenadas de las 4 esquinas visibles para cargar solo
     * los reportes de esa área.
     *
     * @param latMin Latitud mínima (borde sur)
     * @param latMax Latitud máxima (borde norte)
     * @param lngMin Longitud mínima (borde oeste)
     * @param lngMax Longitud máxima (borde este)
     * @return Lista de ReportResponseDTO con los reportes encontrados
     * @throws IllegalArgumentException si los parámetros son inválidos
     */
    public List<ReportResponseDTO> findReportsByZone(double latMin, double latMax,
                                                     double lngMin, double lngMax) {
        // Validar que min < max
        if (latMin > latMax) {
            throw new IllegalArgumentException("latMin no puede ser mayor que latMax");
        }
        if (lngMin > lngMax) {
            throw new IllegalArgumentException("lngMin no puede ser mayor que lngMax");
        }

        // Validar rangos de coordenadas
        validateCoordinates(latMin, lngMin);
        validateCoordinates(latMax, lngMax);

        List<Report> reports = reportRepository.findByZone(latMin, latMax, lngMin, lngMax);
        return reports.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ──────────── MÉTODOS PRIVADOS ────────────

    /**
     * Convierte una entidad Report → ReportResponseDTO.
     *
     * Se usa el patrón Builder de Lombok para crear el DTO.
     * En un proyecto más grande, se usaría MapStruct para esto
     * (ya está configurado en pom.xml), pero para pocos campos
     * el mapeo manual es más explícito y fácil de entender.
     */
    private ReportResponseDTO toResponseDTO(Report report) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .description(report.getDescription())
                .incidentType(report.getIncidentType())
                .address(report.getAddress())
                .status(report.getStatus())
                .source(report.getSource())
                .latitude(report.getLatitude())
                .longitude(report.getLongitude())
                .reportDate(report.getReportDate())
                .build();
    }

    /**
     * Valida que las coordenadas estén dentro de los rangos geográficos válidos.
     * Latitud:  -90 a 90   (sur a norte)
     * Longitud: -180 a 180 (oeste a este)
     */
    private void validateCoordinates(double lat, double lng) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException(
                    "La latitud debe estar entre -90 y 90. Valor recibido: " + lat);
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException(
                    "La longitud debe estar entre -180 y 180. Valor recibido: " + lng);
        }
    }
}
