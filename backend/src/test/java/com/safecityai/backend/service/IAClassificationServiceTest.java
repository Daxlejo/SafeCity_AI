package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para IAClassificationService.
 * Se testea la lógica de heurística (fallback) ya que el API externo se mockea.
 */
@ExtendWith(MockitoExtension.class)
class IAClassificationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationUserService notificationUserService;

    @InjectMocks
    private IAClassificationService iaService;

    private Report validReport;
    private Report gibberishReport;
    private Report vagueReport;

    @BeforeEach
    void setUp() {
        // Forzar que classifyWithAI falle → cae al fallback heurístico
        ReflectionTestUtils.setField(iaService, "openRouterApiKey", "");
        ReflectionTestUtils.setField(iaService, "openRouterModel", "test-model");

        validReport = Report.builder()
                .id(1L)
                .description(
                        "Robo a mano armada en la calle 18 con carrera 27, dos sujetos en moto asaltaron a un transeúnte")
                .incidentType(IncidentType.ROBBERY)
                .address("Calle 18 #27, Pasto")
                .source(ReportSource.CITIZEN_TEXT)
                .latitude(1.2136)
                .longitude(-77.2811)
                .status(ReportStatus.PENDING)
                .build();

        gibberishReport = Report.builder()
                .id(2L)
                .description("asdkjhfkajshdf kjahsdf")
                .incidentType(IncidentType.OTHER)
                .address("Test")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.PENDING)
                .build();

        vagueReport = Report.builder()
                .id(3L)
                .description("Algo pasó aquí cerca de la zona")
                .incidentType(IncidentType.OTHER)
                .address("Cerca")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.PENDING)
                .build();

        vagueReport = Report.builder()
                .id(4L)
                .description("Carrera de ranas causa choque")
                .incidentType(IncidentType.ACCIDENT)
                .address("Cerca de la universidad")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.PENDING)
                .build();
    }

    @Nested
    @DisplayName("classifyReport — Heurística (fallback)")
    class HeuristicClassification {

        @Test
        @DisplayName("Reporte válido con GPS y descripción detallada → score alto")
        void validReport_shouldGetHighScore() {
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            IAClassificationDTO result = iaService.classifyReport(1L);

            assertThat(result).isNotNull();
            assertThat(result.getTrustScore()).isGreaterThanOrEqualTo(50.0);
            assertThat(result.getSuggestedType()).isEqualTo(IncidentType.ROBBERY);
            assertThat(result.getShouldVerify()).isTrue();
            assertThat(result.getReasoning()).contains("Fallback heuristico");
        }

        @Test
        @DisplayName("Reporte gibberish → score 0")
        void gibberishReport_shouldGetZeroScore() {
            when(reportRepository.findById(2L)).thenReturn(Optional.of(gibberishReport));
            when(reportRepository.save(any(Report.class))).thenReturn(gibberishReport);

            IAClassificationDTO result = iaService.classifyReport(2L);

            assertThat(result.getTrustScore()).isEqualTo(0.0);
            assertThat(result.getTrustLevel()).isEqualTo(TrustLevel.UNTRUSTED);
            assertThat(result.getShouldVerify()).isFalse();
        }

        @Test
        @DisplayName("Reporte no encontrado → RuntimeException")
        void nonExistentReport_shouldThrow() {
            when(reportRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> iaService.classifyReport(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("no encontrado");
        }

        @Test
        @DisplayName("Trust score se guarda en el reporte")
        void classifyReport_shouldSaveTrustScore() {
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            iaService.classifyReport(1L);

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());
            assertThat(captor.getValue().getTrustScore()).isNotNull();
            assertThat(captor.getValue().getTrustScore()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("classifyAsync — Flujo completo con notificaciones")
    class AsyncClassification {

        @Test
        @DisplayName("Score >= 50 → auto-verifica y notifica al usuario")
        void highScore_shouldVerifyAndNotify() {
            User owner = User.builder().id(10L).name("Test User").email("test@test.com").build();
            validReport.setReportedBy(owner);

            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            iaService.classifyAsync(1L);

            // Debe cambiar status a VERIFIED
            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository, atLeastOnce()).save(captor.capture());

            // Debe notificar por WebSocket
            verify(notificationService, atLeastOnce()).notifyReportUpdated(any());

            // Debe crear notificación persistente para el usuario
            verify(notificationUserService).createNotification(
                    eq(owner), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Score == 0 → elimina reporte y notifica rechazo")
        void zeroScore_shouldDeleteAndNotify() {
            User owner = User.builder().id(10L).name("Test User").email("test@test.com").build();
            gibberishReport.setReportedBy(owner);

            when(reportRepository.findById(2L)).thenReturn(Optional.of(gibberishReport));
            when(reportRepository.save(any(Report.class))).thenReturn(gibberishReport);

            iaService.classifyAsync(2L);

            // Debe eliminar el reporte
            verify(reportRepository).delete(gibberishReport);

            // Debe notificar eliminación por WebSocket
            verify(notificationService).notifyReportDeleted(2L);

            // Debe crear notificación de rechazo
            verify(notificationUserService).createNotification(
                    eq(owner), isNull(), eq("Reporte rechazado"),
                    contains("rechazado"), eq("ALERT"));
        }

        @Test
        @DisplayName("Sin reportedBy → no crea notificación persistente pero sí funciona")
        void noOwner_shouldWorkWithoutNotification() {
            // validReport no tiene reportedBy
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            iaService.classifyAsync(1L);

            // No debe crear notificación persistente
            verify(notificationUserService, never()).createNotification(
                    any(), any(), anyString(), anyString(), anyString());

            // Pero sí notifica por WebSocket
            verify(notificationService, atLeastOnce()).notifyReportUpdated(any());
        }
    }
}
