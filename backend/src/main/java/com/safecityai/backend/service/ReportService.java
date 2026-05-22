package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportQuotaDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.exception.RateLimitExceededException;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.UserRole;
import com.safecityai.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;
    private final IAClassificationService iaClassificationService;
    private final GeocodingService geocodingService;
    private final UserService userService;

    @Transactional
    public ReportResponseDTO createReport(ReportCreateDTO dto, String userEmail) {
        log.info("Creando nuevo reporte de tipo: {}", dto.getIncidentType());

        Report report = convertToEntity(dto);

        // ═══════════════ REVERSE GEOCODING ═══════════════
        // Si el usuario envió coordenadas GPS pero NO dirección real,
        // convertimos las coordenadas a nombre de barrio automáticamente
        // Ejemplo: (1.2136, -77.2784) → "Anganoy, Pasto"
        // También detecta si el frontend envió coords como address ("1.19951, -77.28434")
        if (report.getLatitude() != null && report.getLongitude() != null) {
            boolean needsGeocoding = report.getAddress() == null
                    || report.getAddress().isBlank()
                    || report.getAddress().matches("^-?\\d+\\.\\d+,\\s*-?\\d+\\.\\d+$");
            if (needsGeocoding) {
                String address = geocodingService.reverseGeocode(
                        report.getLatitude(), report.getLongitude());
                report.setAddress(address);
                log.info("Geocoding: ({}, {}) → {}", report.getLatitude(), report.getLongitude(), address);
            }
        }

        // Vincular reporte al usuario autenticado
        if (userEmail != null) {
            try {
                User user = userService.findByEmail(userEmail);
                report.setReportedBy(user);

                // ═══════════════ RATE-LIMITING POR TRUESCORE ═══════════════
                // Administradores no tienen límite.
                // Los demás usuarios tienen un máximo de reportes por hora fija
                // (ej. 2:00-2:59, 3:00-3:59) según su nivel de confianza.
                if (user.getRole() != UserRole.ADMIN) {
                    enforceRateLimit(user);
                }
            } catch (RateLimitExceededException e) {
                throw e; // Re-lanzar para que el GlobalExceptionHandler la maneje
            } catch (Exception e) {
                log.warn("No se pudo vincular usuario {} al reporte: {}", userEmail, e.getMessage());
            }
        }

        Report savedReport = reportRepository.save(report);
        ReportResponseDTO response = convertToDTO(savedReport);

        // Notificar DESPUÉS del save() para garantizar que el reporte existe en BD
        notificationService.notifyNewReport(response);

        // ═══════════════ IA: CLASIFICAR EN BACKGROUND (ASYNC) ═══════════════
        // classifyAsync() corre en otro hilo (Thread Pool "iaExecutor")
        // IMPORTANTE: Registramos la llamada para DESPUÉS del commit de la transacción.
        // Si llamamos classifyAsync() directamente aquí, el @Async corre en otro hilo
        // pero la transacción de createReport AÚN NO ha hecho commit → "Reporte no encontrado".
        final Long newReportId = savedReport.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                iaClassificationService.classifyAsync(newReportId);
            }
        });

        log.info("Reporte creado exitosamente con ID: {}", savedReport.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public ReportResponseDTO getReportById(Long id) {
        log.debug("Buscando reporte con ID: {}", id);
        Report report = findReportOrThrow(id);
        return convertToDTO(report);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponseDTO> getAllReports(Pageable pageable) {
        log.debug("Listando reportes públicos (sin rechazados) - página: {}, tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        return reportRepository.findByStatusNot(ReportStatus.REJECTED, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponseDTO> getAllReportsIncludingRejected(Pageable pageable) {
        log.debug("Listando TODOS los reportes (admin) - página: {}, tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        return reportRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    // Actualización null-safe: solo modifica campos que el cliente envió
    @Transactional
    public ReportResponseDTO updateReport(Long id, ReportCreateDTO dto) {
        log.info("Actualizando reporte con ID: {}", id);

        Report report = findReportOrThrow(id);
        updateEntityFields(report, dto);
        Report updatedReport = reportRepository.save(report);
        ReportResponseDTO response = convertToDTO(updatedReport);

        notificationService.notifyReportUpdated(response);

        log.info("Reporte ID: {} actualizado exitosamente", id);
        return response;
    }

    @Transactional
    public void deleteReport(Long id) {
        log.info("Eliminando reporte con ID: {}", id);

        if (!reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reporte", "id", id);
        }
        reportRepository.deleteById(id);
        notificationService.notifyReportDeleted(id);

        log.info("Reporte ID: {} eliminado exitosamente", id);
    }

    // Actualizar status de un reporte (para moderacion admin)
    @Transactional
    public void updateStatus(Long id, ReportStatus newStatus) {
        Report report = findReportOrThrow(id);
        report.setStatus(newStatus);
        report = reportRepository.save(report);
        
        // ¡CRUCIAL para que los usuarios vean el cambio de estado en vivo!
        notificationService.notifyReportUpdated(convertToDTO(report));
        
        log.info("Reporte ID: {} actualizado a status: {}", id, newStatus);
    }

    // ═══════════════ RATE-LIMITING: CONSULTA PÚBLICA ═══════════════

    /**
     * Obtiene la cuota de reportes del usuario para la hora actual.
     * El frontend usa este dato para mostrar "Te quedan X de Y reportes esta hora".
     */
    @Transactional(readOnly = true)
    public ReportQuotaDTO getReportQuota(String userEmail) {
        User user = userService.findByEmail(userEmail);

        // Administradores no tienen límite: devolvemos valores simbólicos
        if (user.getRole() == UserRole.ADMIN) {
            return ReportQuotaDTO.builder()
                    .limit(-1)
                    .used(0)
                    .remaining(-1)
                    .resetsAt("Sin límite")
                    .build();
        }

        int maxReports = calculateMaxReports(user.getTrustLevel());
        LocalDateTime windowStart = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime windowEnd = windowStart.plusHours(1);
        long used = reportRepository.countByUserInTimeWindow(user.getId(), windowStart, windowEnd);

        return ReportQuotaDTO.builder()
                .limit(maxReports)
                .used((int) used)
                .remaining(Math.max(0, maxReports - (int) used))
                .resetsAt(windowEnd.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    // ═══════════════ HELPERS ═══════════════
    // Nos ayudan a mantener el codigo limpio y organizado

    /**
     * Valida que el usuario no haya excedido su cuota de reportes por hora.
     * Lanza RateLimitExceededException (HTTP 429) si se excedió el límite.
     */
    private void enforceRateLimit(User user) {
        int maxReports = calculateMaxReports(user.getTrustLevel());
        LocalDateTime windowStart = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime windowEnd = windowStart.plusHours(1);
        long used = reportRepository.countByUserInTimeWindow(user.getId(), windowStart, windowEnd);

        if (used >= maxReports) {
            String resetsAt = windowEnd.format(DateTimeFormatter.ofPattern("HH:mm"));
            log.warn("Rate limit alcanzado para usuario {} (TrustLevel: {}, Usado: {}/{})",
                    user.getEmail(), user.getTrustLevel(), used, maxReports);
            throw new RateLimitExceededException(maxReports, (int) used, resetsAt);
        }

        log.debug("Rate limit OK para usuario {} ({}/{})",
                user.getEmail(), used, maxReports);
    }

    /**
     * Calcula el máximo de reportes por hora según el TrustLevel del usuario.
     * TrustLevel < 65  → 3 reportes/hora
     * TrustLevel 65-74 → 4 reportes/hora
     * TrustLevel >= 75 → 5 reportes/hora
     */
    private int calculateMaxReports(Double trustLevel) {
        double level = (trustLevel != null) ? trustLevel : 50.0;
        if (level >= 75) return 5;
        if (level >= 65) return 4;
        return 3;
    }

    // DRY: centraliza búsqueda + excepción. Punto único para agregar cache o
    // auditoría.
    private Report findReportOrThrow(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Reporte no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Reporte", "id", id);
                });
    }

    // Null-safe: si un campo llega null, preserva el valor existente en BD
    private void updateEntityFields(Report report, ReportCreateDTO dto) {
        if (dto.getDescription() != null)
            report.setDescription(dto.getDescription());
        if (dto.getIncidentType() != null)
            report.setIncidentType(dto.getIncidentType());
        if (dto.getAddress() != null)
            report.setAddress(dto.getAddress());
        if (dto.getSource() != null)
            report.setSource(dto.getSource());
        if (dto.getLatitude() != null)
            report.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null)
            report.setLongitude(dto.getLongitude());
        if (dto.getPhotoUrl() != null)
            report.setPhotoUrl(dto.getPhotoUrl());
    }

    private Report convertToEntity(ReportCreateDTO dto) {
        Report.ReportBuilder builder = Report.builder()
                .description(dto.getDescription())
                .incidentType(dto.getIncidentType())
                .address(dto.getAddress())
                .source(dto.getSource())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .photoUrl(dto.getPhotoUrl())
                .zoneId(dto.getZoneId())
                .status(ReportStatus.PENDING);

        // Parsear fecha del incidente si la enviaron
        if (dto.getIncidentDate() != null && !dto.getIncidentDate().isBlank()) {
            try {
                builder.incidentDate(java.time.LocalDateTime.parse(dto.getIncidentDate()));
            } catch (Exception e) {
                log.warn("No se pudo parsear incidentDate '{}': {}", dto.getIncidentDate(), e.getMessage());
            }
        }

        return builder.build();
    }

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
                .photoUrl(report.getPhotoUrl())
                .trustScore(report.getTrustScore())
                .aiAnalysis(report.getAiAnalysis())
                .zoneId(report.getZoneId())
                .reportDate(report.getReportDate())
                .incidentDate(report.getIncidentDate())
                .build();
    }
}
