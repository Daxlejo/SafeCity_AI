package com.safecityai.backend.service;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.OsintConfig;
import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.OsintNewsArticleRepository;
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
 * Tests unitarios para OsintService V2.
 * Usa deduplicación geoespacial+temporal (existsNearbyDuplicate), no hash.
 */
@ExtendWith(MockitoExtension.class)
class OsintServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private OsintNewsArticleRepository newsArticleRepository;
    @Mock private GeocodingService geocodingService;
    @Mock private AIClient aiClient;
    @Mock private NotificationService notificationService;
    @Mock private OsintConfigService osintConfigService;

    @InjectMocks
    private OsintService osintService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(osintService, "rapidApiKey", "test-key");
        ReflectionTestUtils.setField(osintService, "facebookHost", "facebook-scraper3.p.rapidapi.com");
        ReflectionTestUtils.setField(osintService, "schedulerEnabled", false);
        ReflectionTestUtils.setField(osintService, "defaultCity", "Pasto");
        ReflectionTestUtils.setField(osintService, "maxItemsPerExecution", 10);
        ReflectionTestUtils.setField(osintService, "dedupRadiusMeters", 500.0);
        ReflectionTestUtils.setField(osintService, "dedupHours", 12);
    }

    @Nested
    @DisplayName("scanAndClassify — Deduplicación geoespacial")
    class Deduplication {

        @Test
        @DisplayName("Sin resultados OSINT → no crea reportes")
        void noResults_shouldCreateNothing() {
            OsintService spySvc = spy(osintService);
            doReturn(java.util.List.of()).when(spySvc).searchIncidents(anyString());

            Map<String, Object> result = spySvc.scanAndClassify("Pasto");

            assertThat(result.get("reportsCreated")).isEqualTo(0);
            verify(reportRepository, never()).save(any(Report.class));
        }

        @Test
        @DisplayName("AI descarta el artículo (no es incidente de seguridad) → se omite")
        void aiDiscardsArticle_shouldBeSkipped() throws Exception {
            OsintService spySvc = spy(osintService);

            OsintResultDTO rawResult = OsintResultDTO.builder()
                    .title("Política")
                    .content("El alcalde habló sobre presupuesto")
                    .sourceType(ReportSource.SOCIAL_MEDIA)
                    .detectedLocation("Pasto")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.5)
                    .build();

            doReturn(java.util.List.of(rawResult)).when(spySvc).searchIncidents(anyString());
            // AI devuelve isSecurityIncident=false
            when(aiClient.sendRawPrompt(anyString(), anyString()))
                    .thenReturn("{\"isSecurityIncident\":false,\"incidentType\":\"OTHER\"," +
                            "\"exactAddress\":\"\",\"cleanSummary\":\"\",\"estimatedDate\":\"\"," +
                            "\"trustScore\":10,\"shouldVerify\":false}");

            Map<String, Object> result = spySvc.scanAndClassify("Pasto");

            assertThat(result.get("reportsCreated")).isEqualTo(0);
            assertThat(result.get("discardedByAI")).isEqualTo(1);
            verify(reportRepository, never()).save(any(Report.class));
        }
    }

    @Nested
    @DisplayName("scanAndClassify — Filtro de antigüedad")
    class AgeFilter {

        @Test
        @DisplayName("Reporte > 7 días → se ignora sin llamar a la IA")
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
            verify(aiClient, never()).sendRawPrompt(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("scanAndClassify — Reporte creado correctamente")
    class ReportCreation {

        @Test
        @DisplayName("Reporte OSINT válido con geocodificación → se crea con source y tipo correctos")
        void shouldCreateWithCorrectDefaults() {
            OsintService spySvc = spy(osintService);

            OsintResultDTO osintResult = OsintResultDTO.builder()
                    .title("Hurto")
                    .content("Hurto en el centro de Pasto")
                    .sourceType(null)
                    .detectedLocation("Pasto Centro")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.6)
                    .build();

            doReturn(java.util.List.of(osintResult)).when(spySvc).searchIncidents(anyString());

            // AI responde que es un incidente con dirección geocodificable
            when(aiClient.sendRawPrompt(anyString(), anyString()))
                    .thenReturn("{\"isSecurityIncident\":true,\"incidentType\":\"ROBBERY\"," +
                            "\"exactAddress\":\"Calle 18 con Carrera 25\",\"cleanSummary\":\"Hurto en el centro\"," +
                            "\"estimatedDate\":\"\",\"trustScore\":65,\"shouldVerify\":true}");

            // Geocodificación retorna coordenadas válidas
            double[] coords = {1.2136, -77.2784};
            when(geocodingService.geocode(anyString())).thenReturn(coords);

            // No hay duplicado geoespacial
            when(reportRepository.existsNearbyDuplicate(any(), anyDouble(), anyDouble(),
                    anyDouble(), anyDouble(), any())).thenReturn(false);

            Report savedReport = Report.builder().id(5L).build();
            when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

            Map<String, Object> result = spySvc.scanAndClassify("Pasto");

            assertThat(result.get("reportsCreated")).isEqualTo(1);
            assertThat(result.get("duplicatesSkipped")).isEqualTo(0);

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());

            Report created = captor.getValue();
            assertThat(created.getSource()).isEqualTo(ReportSource.SOCIAL_MEDIA);
            assertThat(created.getIncidentType()).isEqualTo(IncidentType.ROBBERY);
            assertThat(created.getLatitude()).isEqualTo(1.2136);
            assertThat(created.getLongitude()).isEqualTo(-77.2784);
        }

        @Test
        @DisplayName("Sin geocodificación (dirección vacía del AI) → se guarda como noticia, no como reporte")
        void noGeocoding_shouldSaveAsNewsArticle() {
            OsintService spySvc = spy(osintService);

            OsintResultDTO osintResult = OsintResultDTO.builder()
                    .title("Incidente")
                    .content("Robo en zona desconocida de Pasto")
                    .sourceType(ReportSource.INSTITUTIONAL)
                    .detectedLocation("Pasto")
                    .publishedAt(LocalDateTime.now())
                    .confidence(0.7)
                    .build();

            doReturn(java.util.List.of(osintResult)).when(spySvc).searchIncidents(anyString());

            // AI responde con dirección vacía → geocoding no se invoca
            when(aiClient.sendRawPrompt(anyString(), anyString()))
                    .thenReturn("{\"isSecurityIncident\":true,\"incidentType\":\"ROBBERY\"," +
                            "\"exactAddress\":\"\",\"cleanSummary\":\"Robo en zona desconocida\"," +
                            "\"estimatedDate\":\"\",\"trustScore\":40,\"shouldVerify\":false}");

            // exactAddress vacío → geocodingService.geocode() no se llama
            when(newsArticleRepository.existsByContentHash(anyString())).thenReturn(false);

            Map<String, Object> result = spySvc.scanAndClassify("Pasto");

            assertThat(result.get("reportsCreated")).isEqualTo(0);
            assertThat(result.get("savedAsNews")).isEqualTo(1);
            verify(reportRepository, never()).save(any(Report.class));
            verify(newsArticleRepository).save(any(OsintNewsArticle.class));
            // geocodingService NO debe haberse llamado porque la dirección está vacía
            verify(geocodingService, never()).geocode(anyString());
        }
    }

    @Nested
    @DisplayName("scheduledScan")
    class Scheduler {

        @Test
        @DisplayName("Scheduler desactivado → no ejecuta scan")
        void schedulerDisabled_shouldSkip() {
            ReflectionTestUtils.setField(osintService, "schedulerEnabled", false);

            OsintConfig mockConfig = OsintConfig.builder().enabled(true).build();
            when(osintConfigService.getActiveConfig()).thenReturn(mockConfig);

            osintService.scheduledScan();

            verify(reportRepository, never()).save(any());
            verify(aiClient, never()).sendRawPrompt(anyString(), anyString());
        }
    }
}
