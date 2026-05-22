package com.safecityai.backend.service;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.User;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.model.enums.TrustLevel;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para IAClassificationService (Single-Layer Multimodal).
 * AIClient se mockea: se fuerza fallo para probar el fallback heurístico,
 * o se retorna un DTO predefinido para probar la lógica de decisión.
 */
@ExtendWith(MockitoExtension.class)
class IAClassificationServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationUserService notificationUserService;
    @Mock private AIClient aiClient;

    @InjectMocks
    private IAClassificationService iaService;

    private Report validReport;
    private Report gibberishReport;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(iaService, "uploadDir", "uploads");

        validReport = Report.builder()
                .id(1L)
                .description("Robo a mano armada en la calle 18 con carrera 27, dos sujetos en moto asaltaron a un transeúnte")
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
    }

    @Nested
    @DisplayName("classifyReport — Heurística (fallback cuando AIClient falla)")
    class HeuristicClassification {

        @Test
        @DisplayName("Reporte válido con GPS y descripción detallada → score alto (fallback)")
        void validReport_shouldGetHighScore() {
            // Forzar que el AIClient lance excepción → cae al fallback heurístico
            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong()))
                    .thenThrow(new RuntimeException("AI unavailable"));
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            IAClassificationDTO result = iaService.classifyReport(1L);

            assertThat(result).isNotNull();
            assertThat(result.getTrustScore()).isGreaterThanOrEqualTo(50.0);
            assertThat(result.getSuggestedType()).isEqualTo(IncidentType.ROBBERY);
            assertThat(result.getReasoning()).contains("[Heuristic Fallback]");
        }

        @Test
        @DisplayName("Reporte gibberish → score 0 (fallback)")
        void gibberishReport_shouldGetZeroScore() {
            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong()))
                    .thenThrow(new RuntimeException("AI unavailable"));
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
            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong()))
                    .thenThrow(new RuntimeException("AI unavailable"));
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
    @DisplayName("classifyReport — AI responde correctamente (single-layer)")
    class AIClassification {

        @Test
        @DisplayName("AI retorna VERIFIED → reporte queda verificado")
        void aiReturnsVerified_shouldSetVerified() {
            IAClassificationDTO aiResult = IAClassificationDTO.builder()
                    .reportId(1L)
                    .trustScore(80.0)
                    .trustLevel(TrustLevel.VERIFIED)
                    .suggestedType(IncidentType.ROBBERY)
                    .reasoning("[IA gpt-4o-mini] Reporte concreto con detalles accionables.")
                    .statusDecision(ReportStatus.VERIFIED)
                    .shouldVerify(true)
                    .build();

            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong())).thenReturn(aiResult);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            IAClassificationDTO result = iaService.classifyReport(1L);

            assertThat(result.getTrustScore()).isEqualTo(80.0);
            assertThat(result.getStatusDecision()).isEqualTo(ReportStatus.VERIFIED);
            assertThat(result.getSuggestedType()).isEqualTo(IncidentType.ROBBERY);
        }

        @Test
        @DisplayName("AI retorna REJECTED → score 0")
        void aiReturnsRejected_shouldHaveZeroScore() {
            IAClassificationDTO aiResult = IAClassificationDTO.builder()
                    .reportId(2L)
                    .trustScore(0.0)
                    .trustLevel(TrustLevel.UNTRUSTED)
                    .suggestedType(IncidentType.OTHER)
                    .reasoning("[IA gpt-4o-mini] Contenido no válido.")
                    .statusDecision(ReportStatus.REJECTED)
                    .shouldVerify(false)
                    .build();

            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong())).thenReturn(aiResult);
            when(reportRepository.findById(2L)).thenReturn(Optional.of(gibberishReport));
            when(reportRepository.save(any(Report.class))).thenReturn(gibberishReport);

            IAClassificationDTO result = iaService.classifyReport(2L);

            assertThat(result.getTrustScore()).isEqualTo(0.0);
            assertThat(result.getStatusDecision()).isEqualTo(ReportStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("classifyAsync — Flujo completo con notificaciones")
    class AsyncClassification {

        @Test
        @DisplayName("AI retorna VERIFIED → notifica al usuario con bonus de reputación")
        void highScore_shouldVerifyAndNotify() {
            User owner = User.builder().id(10L).name("Test User").email("test@test.com")
                    .trustLevel(50.0).build();
            validReport.setReportedBy(owner);

            IAClassificationDTO aiResult = IAClassificationDTO.builder()
                    .reportId(1L).trustScore(75.0).trustLevel(TrustLevel.HIGH)
                    .suggestedType(IncidentType.ROBBERY).reasoning("Reporte concreto.")
                    .statusDecision(ReportStatus.VERIFIED).shouldVerify(true).build();

            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong())).thenReturn(aiResult);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);
            lenient().when(reportRepository.findAverageTrustScoreByUser(anyLong())).thenReturn(50.0);
            lenient().when(userRepository.save(any(User.class))).thenReturn(owner);

            iaService.classifyAsync(1L);

            verify(reportRepository, atLeastOnce()).save(any(Report.class));
            verify(notificationService, atLeastOnce()).notifyReportUpdated(any());
            verify(notificationUserService).createNotification(
                    eq(owner), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("AI retorna REJECTED → elimina reporte y penaliza reputación")
        void zeroScore_shouldDeleteAndNotify() {
            User owner = User.builder().id(10L).name("Test User").email("test@test.com")
                    .trustLevel(50.0).build();
            gibberishReport.setReportedBy(owner);

            IAClassificationDTO aiResult = IAClassificationDTO.builder()
                    .reportId(2L).trustScore(0.0).trustLevel(TrustLevel.UNTRUSTED)
                    .suggestedType(IncidentType.OTHER).reasoning("Contenido inválido.")
                    .statusDecision(ReportStatus.REJECTED).shouldVerify(false).build();

            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong())).thenReturn(aiResult);
            when(reportRepository.findById(2L)).thenReturn(Optional.of(gibberishReport));
            when(reportRepository.save(any(Report.class))).thenReturn(gibberishReport);
            lenient().when(reportRepository.findAverageTrustScoreByUser(anyLong())).thenReturn(null);
            lenient().when(userRepository.save(any(User.class))).thenReturn(owner);

            iaService.classifyAsync(2L);

            verify(reportRepository).delete(gibberishReport);
            verify(notificationService).notifyReportDeleted(2L);
            verify(notificationUserService).createNotification(
                    eq(owner), isNull(), eq("⚠️ Reporte rechazado"),
                    contains("rechazado"), eq("ALERT"));
        }

        @Test
        @DisplayName("Sin reportedBy → funciona sin crear notificación persistente")
        void noOwner_shouldWorkWithoutNotification() {
            IAClassificationDTO aiResult = IAClassificationDTO.builder()
                    .reportId(1L).trustScore(70.0).trustLevel(TrustLevel.HIGH)
                    .suggestedType(IncidentType.ROBBERY).reasoning("Reporte válido.")
                    .statusDecision(ReportStatus.VERIFIED).shouldVerify(true).build();

            when(aiClient.classifyMultimodal(anyString(), anyList(), anyLong())).thenReturn(aiResult);
            when(reportRepository.findById(1L)).thenReturn(Optional.of(validReport));
            when(reportRepository.save(any(Report.class))).thenReturn(validReport);

            iaService.classifyAsync(1L);

            verify(notificationUserService, never()).createNotification(
                    any(), any(), anyString(), anyString(), anyString());
            verify(notificationService, atLeastOnce()).notifyReportUpdated(any());
        }
    }
}
