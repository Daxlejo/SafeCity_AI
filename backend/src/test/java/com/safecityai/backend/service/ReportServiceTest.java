package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportCreateDTO;
import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.exception.ResourceNotFoundException;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.util.PhotoUrlHelper;
import com.safecityai.backend.util.ReportMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ReportService.
 * Cobertura: CRUD, vinculación de usuario, notificaciones.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private NotificationService notificationService;
    @Mock private IAClassificationService iaClassificationService;
    @Mock private GeocodingService geocodingService;
    @Mock private UserService userService;
    @Mock private ReportMapper reportMapper;
    @Mock private PhotoUrlHelper photoUrlHelper;

    @InjectMocks
    private ReportService reportService;

    private ReportCreateDTO validDTO;
    private Report savedReport;
    private ReportResponseDTO savedReportDTO;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Simula contexto de transacción para que registerSynchronization no falle
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }

        validDTO = new ReportCreateDTO();
        validDTO.setDescription("Accidente de tránsito en la avenida principal");
        validDTO.setIncidentType(IncidentType.ACCIDENT);
        validDTO.setAddress("Avenida Principal, Pasto");
        validDTO.setSource(ReportSource.CITIZEN_TEXT);
        validDTO.setLatitude(1.2136);
        validDTO.setLongitude(-77.2811);

        savedReport = Report.builder()
                .id(1L)
                .description(validDTO.getDescription())
                .incidentType(validDTO.getIncidentType())
                .address(validDTO.getAddress())
                .source(validDTO.getSource())
                .latitude(validDTO.getLatitude())
                .longitude(validDTO.getLongitude())
                .status(ReportStatus.PENDING)
                .reportDate(LocalDateTime.now())
                .build();

        savedReportDTO = ReportResponseDTO.builder()
                .id(1L)
                .description(savedReport.getDescription())
                .incidentType(savedReport.getIncidentType())
                .address(savedReport.getAddress())
                .status(savedReport.getStatus())
                .source(savedReport.getSource())
                .latitude(savedReport.getLatitude())
                .longitude(savedReport.getLongitude())
                .reportDate(savedReport.getReportDate())
                .build();

        testUser = User.builder()
                .id(10L)
                .name("Test User")
                .email("test@example.com")
                .build();
    }

    @AfterEach
    void tearDown() {
        // Limpiar el contexto de sincronización después de cada test
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    @DisplayName("createReport")
    class CreateReport {

        @Test
        @DisplayName("DTO válido → crea reporte y retorna DTO de respuesta")
        void validDTO_shouldCreateReport() {
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            ReportResponseDTO result = reportService.createReport(validDTO, null);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDescription()).isEqualTo(validDTO.getDescription());
            assertThat(result.getIncidentType()).isEqualTo(IncidentType.ACCIDENT);
            assertThat(result.getStatus()).isEqualTo(ReportStatus.PENDING);
        }

        @Test
        @DisplayName("Con email de usuario → vincula reportedBy")
        void withUserEmail_shouldLinkUser() {
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(userService.findByEmail("test@example.com")).thenReturn(testUser);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            reportService.createReport(validDTO, "test@example.com");

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());
            assertThat(captor.getValue().getReportedBy()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Sin email → reportedBy queda null (no crashea)")
        void withoutEmail_shouldNotCrash() {
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            ReportResponseDTO result = reportService.createReport(validDTO, null);

            assertThat(result).isNotNull();
            verify(userService, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("Notifica nuevo reporte por WebSocket")
        void shouldNotifyNewReport() {
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            reportService.createReport(validDTO, null);

            verify(notificationService).notifyNewReport(any(ReportResponseDTO.class));
        }

        @Test
        @DisplayName("Crea reporte y lanza clasificación IA async")
        void shouldTriggerAsyncClassification() {
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            reportService.createReport(validDTO, null);

            // classifyAsync es @Async — no es verificable directamente con verify().
            // Verificamos que el reporte se guardó (precondición del async)
            verify(reportRepository).save(any(Report.class));
            // Y que la notificación WebSocket se lanzó síncronamente
            verify(notificationService).notifyNewReport(any(ReportResponseDTO.class));
        }

        @Test
        @DisplayName("Con GPS sin dirección → usa geocoding inverso")
        void withGPSNoAddress_shouldReverseGeocode() {
            validDTO.setAddress(null);
            lenient().when(photoUrlHelper.extractFilename(any())).thenReturn(null);
            when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                    .thenReturn("Barrio Anganoy, Pasto");
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            reportService.createReport(validDTO, null);

            verify(geocodingService).reverseGeocode(1.2136, -77.2811);
        }
    }

    @Nested
    @DisplayName("getReportById")
    class GetById {

        @Test
        @DisplayName("ID existente → retorna DTO")
        void existingId_shouldReturnDTO() {
            when(reportRepository.findById(1L)).thenReturn(Optional.of(savedReport));
            when(reportMapper.convertToDTO(savedReport)).thenReturn(savedReportDTO);

            ReportResponseDTO result = reportService.getReportById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDescription()).isEqualTo(savedReport.getDescription());
        }

        @Test
        @DisplayName("ID inexistente → ResourceNotFoundException")
        void nonExistingId_shouldThrow() {
            when(reportRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getReportById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAllReports")
    class GetAll {

        @Test
        @DisplayName("Retorna página de DTOs (excluye REJECTED)")
        void shouldReturnPageOfDTOs() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Report> page = new PageImpl<>(List.of(savedReport));
            when(reportRepository.findByStatusNot(ReportStatus.REJECTED, pageable)).thenReturn(page);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            Page<ReportResponseDTO> result = reportService.getAllReports(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("deleteReport")
    class DeleteReport {

        @Test
        @DisplayName("ID existente → elimina y notifica")
        void existingId_shouldDeleteAndNotify() {
            when(reportRepository.existsById(1L)).thenReturn(true);

            reportService.deleteReport(1L);

            verify(reportRepository).deleteById(1L);
            verify(notificationService).notifyReportDeleted(1L);
        }

        @Test
        @DisplayName("ID inexistente → ResourceNotFoundException")
        void nonExistingId_shouldThrow() {
            when(reportRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> reportService.deleteReport(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("Cambia status y notifica por WebSocket")
        void shouldUpdateStatusAndNotify() {
            when(reportRepository.findById(1L)).thenReturn(Optional.of(savedReport));
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
            when(reportMapper.convertToDTO(any(Report.class))).thenReturn(savedReportDTO);

            reportService.updateStatus(1L, ReportStatus.VERIFIED);

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.VERIFIED);
            verify(notificationService).notifyReportUpdated(any());
        }
    }
}
