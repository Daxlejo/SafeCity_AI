package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capa de servicio para la gestión de reportes ciudadanos.
 *
 * Principios aplicados:
 * - Service Layer Pattern: toda la lógica de negocio vive aquí
 * - DRY (Don't Repeat Yourself): lógica compartida extraída a helpers
 * - SRP (Single Responsibility): cada método tiene una responsabilidad clara
 *
 * @Slf4j: Lombok genera automáticamente un Logger estático llamado 'log'.
 * Permite usar: log.info(), log.warn(), log.error(), log.debug()
 * Esto reemplaza la declaración manual:
 *   private static final Logger log = LoggerFactory.getLogger(ReportService.class);
 *
 * @RequiredArgsConstructor: inyección por constructor de los campos 'final'
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // ══════════════════════ CREAR ══════════════════════

    /**
     * Crea un nuevo reporte a partir de los datos del ciudadano.
     *
     * @Transactional: envuelve todo el método en una transacción de BD.
     * Si ocurre una excepción, se hace ROLLBACK automáticamente.
     * Esto garantiza ATOMICIDAD: o se guarda todo, o no se guarda nada.
     * Es fundamental cuando en el futuro este método haga más de una
     * operación de BD (ej: guardar reporte + actualizar zona de riesgo).
     */
    @Transactional
    public ReportResponseDTO createReport(ReportCreateDTO dto) {
        log.info("Creando nuevo reporte de tipo: {}", dto.getIncidentType());

        Report report = convertToEntity(dto);
        Report savedReport = reportRepository.save(report);

        log.info("Reporte creado exitosamente con ID: {}", savedReport.getId());
        return convertToDTO(savedReport);
    }

    // ══════════════════════ LEER POR ID ══════════════════════

    /**
     * Busca un reporte por su ID.
     *
     * @Transactional(readOnly = true):
     * - Le dice a Hibernate que esta transacción SOLO LEE datos
     * - Hibernate desactiva el "dirty checking" (no revisa si la entidad cambió)
     * - Esto mejora el rendimiento en consultas de solo lectura
     */
    @Transactional(readOnly = true)
    public ReportResponseDTO getReportById(Long id) {
        log.debug("Buscando reporte con ID: {}", id);

        Report report = findReportOrThrow(id);
        return convertToDTO(report);
    }

    // ══════════════════════ LISTAR TODOS ══════════════════════

    /**
     * Lista reportes con PAGINACIÓN.
     *
     * ¿Por qué Pageable en vez de findAll()?
     * - findAll() carga TODOS los registros en memoria
     * - Con 10,000 reportes en Pasto, eso consumiría mucha RAM
     * - Pageable le dice a la BD: "tráeme solo los registros de la página X, de tamaño Y"
     * - La BD hace: SELECT * FROM reports LIMIT Y OFFSET X*Y → mucho más eficiente
     *
     * Page<> vs List<>:
     * - Page contiene: los datos + metadata (total de elementos, total de páginas, etc.)
     * - El frontend puede usar esa metadata para construir la navegación de páginas
     *
     * Page.map() aplica convertToDTO a cada elemento manteniendo la metadata de paginación.
     */
    @Transactional(readOnly = true)
    public Page<ReportResponseDTO> getAllReports(Pageable pageable) {
        log.debug("Listando reportes - página: {}, tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        return reportRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    // ══════════════════════ ACTUALIZAR ══════════════════════

    /**
     * Actualiza un reporte existente con actualización NULL-SAFE.
     *
     * ¿Qué es null-safe?
     * Si el cliente envía un JSON parcial como: {"description": "nuevo texto"}
     * sin incluir incidentType, address, etc. → esos campos llegan como null.
     *
     * SIN null-safe: report.setAddress(null) → borra la dirección existente ❌
     * CON null-safe: if (dto.getAddress() != null) → solo actualiza lo enviado ✅
     *
     * La lógica de actualización se delega a updateEntityFields() (principio SRP).
     */
    @Transactional
    public ReportResponseDTO updateReport(Long id, ReportCreateDTO dto) {
        log.info("Actualizando reporte con ID: {}", id);

        Report report = findReportOrThrow(id);
        updateEntityFields(report, dto);
        Report updatedReport = reportRepository.save(report);

        log.info("Reporte ID: {} actualizado exitosamente", id);
        return convertToDTO(updatedReport);
    }

    // ══════════════════════ ELIMINAR ══════════════════════

    @Transactional
    public void deleteReport(Long id) {
        log.info("Eliminando reporte con ID: {}", id);

        if (!reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reporte", "id", id);
        }
        reportRepository.deleteById(id);

        log.info("Reporte ID: {} eliminado exitosamente", id);
    }

    // ══════════════════════════════════════════════════════════
    // MÉTODOS HELPERS PRIVADOS
    // ══════════════════════════════════════════════════════════

    /**
     * Principio DRY: centraliza la búsqueda por ID + excepción.
     *
     * ANTES: esta lógica estaba duplicada en getReportById() y updateReport().
     * AHORA: ambos métodos llaman a findReportOrThrow() → si algo cambia
     * (ej: agregar auditoría, cache, etc.) se modifica en UN solo lugar.
     *
     * El log.warn() se ejecuta SOLO cuando no se encuentra el reporte,
     * lo cual es útil para detectar ataques o bugs en producción.
     */
    private Report findReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Reporte no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Reporte", "id", id);
                });
    }

    /**
     * Principio SRP (Single Responsibility Principle):
     * La responsabilidad de "cómo actualizar campos" está separada de
     * la responsabilidad de "buscar, guardar y responder".
     *
     * Actualización null-safe: solo modifica campos que el cliente envió.
     * Campos con valor null en el DTO se ignoran, preservando el valor existente.
     */
    private void updateEntityFields(Report report, ReportCreateDTO dto) {
        if (dto.getDescription() != null) report.setDescription(dto.getDescription());
        if (dto.getIncidentType() != null) report.setIncidentType(dto.getIncidentType());
        if (dto.getAddress() != null) report.setAddress(dto.getAddress());
        if (dto.getSource() != null) report.setSource(dto.getSource());
        if (dto.getLatitude() != null) report.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) report.setLongitude(dto.getLongitude());
    }

    /**
     * Convierte ReportCreateDTO → Report (Entity).
     * Solo mapea los campos del ciudadano.
     * Los campos automáticos (id, status, reportDate) los maneja JPA.
     */
    private Report convertToEntity(ReportCreateDTO dto) {
        return Report.builder()
                .description(dto.getDescription())
                .incidentType(dto.getIncidentType())
                .address(dto.getAddress())
                .source(dto.getSource())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();
    }

    /**
     * Convierte Report (Entity) → ReportResponseDTO.
     * Mapea todos los campos incluyendo los generados por el sistema.
     */
    private ReportResponseDTO convertToDTO(Report report) {
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
}
