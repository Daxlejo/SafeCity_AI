package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para OsintService.
 * Testea deduplicación, filtro de antigüedad, y clasificación async.
 * Las llamadas HTTP externas (Google News, Facebook) se testean indirectamente.
 */
@ExtendWith(MockitoExtension.class)
class OsintServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private IAClassificationService iaService;

    @InjectMocks
    private OsintService osintService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(osintService, "rapidApiKey", "test-key");
        ReflectionTestUtils.setField(osintService, "facebookHost", "facebook-scraper3.p.rapidapi.com");
        ReflectionTestUtils.setField(osintService, "schedulerEnabled", false);
        ReflectionTestUtils.setField(osintService, "defaultCity", "Pasto");
    }

    @Nested
    @DisplayName("scanAndClassify — Deduplicación")
    class Deduplication {

        @Test
        @DisplayName("Reporte duplicado → se omite, no se crea en BD")
        void duplicateContent_shouldBeSkipped() {
            // El hash ya existe en BD → duplicado
            when(reportRepository.existsByDescriptionHash(anyString())).thenReturn(true);

            // Mock: simular que searchIncidents retorna 0 resultados
            // (testeamos deduplicación de forma unitaria via el servicio)
            OsintService spySvc = spy(osintService);
            OsintResultDTO result = OsintResultDTO.builder()
                    .title("Test")
                    .content("Robo en la calle 18")
                    .sourceType(ReportSource.SOCIAL_MEDIA)
                    .detectedLocation("Pasto")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.8)
                    .build();

            doReturn(java.util.List.of(result)).when(spySvc).searchIncidents(anyString());

            Map<String, Object> scanResult = spySvc.scanAndClassify("Pasto");

            assertThat(scanResult.get("reportsCreated")).isEqualTo(0);
            assertThat(scanResult.get("duplicatesSkipped")).isEqualTo(1);
            verify(reportRepository, never()).save(any(Report.class));
        }

        @Test
        @DisplayName("Reporte nuevo → se crea en BD y clasifica async")
        void newContent_shouldCreateAndClassify() {
            when(reportRepository.existsByDescriptionHash(anyString())).thenReturn(false);

            Report savedReport = Report.builder()
                    .id(1L)
                    .description("Accidente en la autopista")
                    .incidentType(IncidentType.OTHER)
                    .status(ReportStatus.PENDING)
                    .build();
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

            OsintService spySvc = spy(osintService);
            OsintResultDTO result = OsintResultDTO.builder()
                    .title("Accidente")
                    .content("Accidente en la autopista")
                    .sourceType(ReportSource.INSTITUTIONAL)
                    .detectedLocation("Pasto")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.7)
                    .build();

            doReturn(java.util.List.of(result)).when(spySvc).searchIncidents(anyString());

            Map<String, Object> scanResult = spySvc.scanAndClassify("Pasto");

            assertThat(scanResult.get("reportsCreated")).isEqualTo(1);
            assertThat(scanResult.get("duplicatesSkipped")).isEqualTo(0);
            verify(reportRepository).save(any(Report.class));
            // Verifica que se llama classifyAsync (no classifyReport)
            verify(iaService).classifyAsync(1L);
        }
    }

    @Nested
    @DisplayName("scanAndClassify — Filtro de antigüedad")
    class AgeFilter {

        @Test
        @DisplayName("Reporte > 7 días → se ignora")
        void oldReport_shouldBeIgnored() {
            OsintService spySvc = spy(osintService);
            OsintResultDTO oldResult = OsintResultDTO.builder()
                    .title("Viejo")
                    .content("Robo antiguo")
                    .sourceType(ReportSource.SOCIAL_MEDIA)
                    .detectedLocation("Pasto")
                    .publishedAt(LocalDateTime.now().minusDays(10))
                    .confidence(0.5)
                    .build();

            doReturn(java.util.List.of(oldResult)).when(spySvc).searchIncidents(anyString());

            Map<String, Object> result = spySvc.scanAndClassify("Pasto");

            assertThat(result.get("reportsCreated")).isEqualTo(0);
            verify(reportRepository, never()).save(any(Report.class));
        }
    }

    @Nested
    @DisplayName("scanAndClassify — Reporte creado correctamente")
    class ReportCreation {

        @Test
        @DisplayName("Reporte OSINT se crea con source SOCIAL_MEDIA y tipo OTHER")
        void shouldCreateWithCorrectDefaults() {
            when(reportRepository.existsByDescriptionHash(anyString())).thenReturn(false);

            Report savedReport = Report.builder().id(5L).build();
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

            OsintService spySvc = spy(osintService);
            OsintResultDTO osintResult = OsintResultDTO.builder()
                    .title("Hurto")
                    .content("Hurto en el centro de Pasto")
                    .sourceType(null) // sin tipo explícito
                    .detectedLocation("Pasto Centro")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.6)
                    .build();

            doReturn(java.util.List.of(osintResult)).when(spySvc).searchIncidents(anyString());
            spySvc.scanAndClassify("Pasto");

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());

            Report created = captor.getValue();
            assertThat(created.getSource()).isEqualTo(ReportSource.SOCIAL_MEDIA);
            assertThat(created.getIncidentType()).isEqualTo(IncidentType.OTHER);
            assertThat(created.getStatus()).isEqualTo(ReportStatus.PENDING);
            assertThat(created.getAddress()).isEqualTo("Pasto Centro");
            assertThat(created.getDescriptionHash()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("scheduledScan")
    class Scheduler {

        @Test
        @DisplayName("Scheduler desactivado → no ejecuta scan")
        void schedulerDisabled_shouldSkip() {
            ReflectionTestUtils.setField(osintService, "schedulerEnabled", false);

            // No debería lanzar excepciones ni hacer llamadas
            osintService.scheduledScan();

            verify(reportRepository, never()).save(any());
        }
    }
}
