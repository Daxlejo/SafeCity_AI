package com.safecityai.backend.service;

import com.safecityai.backend.dto.DangerousZoneSummaryDTO;
import com.safecityai.backend.dto.HeatmapPointDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import com.safecityai.backend.repository.UserRepository;
import com.safecityai.backend.repository.ZoneRepository;
import com.safecityai.backend.util.PhotoUrlHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para StatsService.
 * Cubren los métodos de heatmap con grid-cell clustering
 * y la zona más peligrosa de la semana.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private PhotoUrlHelper photoUrlHelper;

    private StatsService statsService;

    private List<Report> sampleReports;

    @BeforeEach
    void setUp() {
        // Construct manually to include the PhotoUrlHelper dependency
        statsService = new StatsService(reportRepository, userRepository, zoneRepository, photoUrlHelper);

        // Crear reportes de prueba en la misma celda geográfica (~200m)
        // Coordenadas base: 1.214, -77.281 (Pasto, Colombia)
        Report r1 = Report.builder()
                .id(1L)
                .latitude(1.2141)
                .longitude(-77.2811)
                .incidentType(IncidentType.ROBBERY)
                .status(ReportStatus.VERIFIED)
                .source(ReportSource.CITIZEN_TEXT)
                .description("Robo en zona centro")
                .address("Calle 18")
                .reportDate(LocalDateTime.now().minusHours(2))
                .build();

        Report r2 = Report.builder()
                .id(2L)
                .latitude(1.2142)
                .longitude(-77.2812)
                .incidentType(IncidentType.ROBBERY)
                .status(ReportStatus.VERIFIED)
                .source(ReportSource.CITIZEN_TEXT)
                .description("Otro robo cerca")
                .address("Calle 19")
                .reportDate(LocalDateTime.now().minusHours(1))
                .build();

        Report r3 = Report.builder()
                .id(3L)
                .latitude(1.2143)
                .longitude(-77.2813)
                .incidentType(IncidentType.ACCIDENT)
                .status(ReportStatus.VERIFIED)
                .source(ReportSource.CITIZEN_TEXT)
                .description("Accidente vial")
                .address("Carrera 27")
                .reportDate(LocalDateTime.now().minusMinutes(30))
                .build();

        // Reporte en celda diferente (alejado ~1km)
        Report r4 = Report.builder()
                .id(4L)
                .latitude(1.225)
                .longitude(-77.290)
                .incidentType(IncidentType.TRAFFIC)
                .status(ReportStatus.VERIFIED)
                .source(ReportSource.CITIZEN_TEXT)
                .description("Embotellamiento")
                .address("Avenida Principal")
                .reportDate(LocalDateTime.now().minusHours(3))
                .build();

        sampleReports = List.of(r1, r2, r3, r4);
    }

    // ═══ HEATMAP TESTS ═══

    @Test
    @DisplayName("getHeatmapData agrupa reportes cercanos en la misma celda")
    void getHeatmapData_shouldClusterNearbyReports() {
        // getHeatmapData() now calls findRecentWithFullCoordinates(since)
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        List<HeatmapPointDTO> result = statsService.getHeatmapData();

        // r1, r2, r3 están en la misma celda (1.214, -77.281)
        // r4 está en otra celda (1.225, -77.290)
        assertEquals(2, result.size(), "Debería agrupar en 2 celdas");
    }

    @Test
    @DisplayName("getHeatmapData retorna weight basado en cantidad de reportes")
    void getHeatmapData_shouldUseReportCountAsWeight() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        List<HeatmapPointDTO> result = statsService.getHeatmapData();

        // La celda con 3 reportes debería tener intensity = 3.0
        HeatmapPointDTO clusterCell = result.stream()
                .filter(p -> p.getIntensity() > 1)
                .findFirst()
                .orElseThrow();

        assertEquals(3.0, clusterCell.getIntensity(), "3 reportes en la celda = weight 3.0");
    }

    @Test
    @DisplayName("getHeatmapData incluye incidentType del tipo más frecuente")
    void getHeatmapData_shouldIncludeMostCommonIncidentType() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        List<HeatmapPointDTO> result = statsService.getHeatmapData();

        // La celda con r1(ROBBERY), r2(ROBBERY), r3(ACCIDENT) → tipo más frecuente = ROBBERY
        HeatmapPointDTO clusterCell = result.stream()
                .filter(p -> p.getIntensity() > 1)
                .findFirst()
                .orElseThrow();

        assertEquals("ROBBERY", clusterCell.getIncidentType(),
                "El tipo más frecuente debería ser ROBBERY (2 de 3)");
    }

    @Test
    @DisplayName("getHeatmapData con lista vacía retorna lista vacía")
    void getHeatmapData_shouldReturnEmptyListWhenNoReports() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<HeatmapPointDTO> result = statsService.getHeatmapData();

        assertTrue(result.isEmpty(), "Sin reportes, el resultado debería ser vacío");
    }

    // ═══ DANGEROUS ZONE OF WEEK TESTS ═══

    @Test
    @DisplayName("getDangerousZoneOfWeek retorna la celda con más reportes")
    void getDangerousZoneOfWeek_shouldReturnHighestCountCell() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        DangerousZoneSummaryDTO result = statsService.getDangerousZoneOfWeek();

        assertNotNull(result, "Debería retornar una zona");
        assertEquals(3L, result.getReportCount(), "La celda con 3 reportes es la más peligrosa");
        assertEquals("ROBBERY", result.getTopIncidentType(), "Tipo más frecuente = ROBBERY");
    }

    @Test
    @DisplayName("getDangerousZoneOfWeek retorna coordenadas promediadas del centro")
    void getDangerousZoneOfWeek_shouldReturnAveragedCoordinates() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        DangerousZoneSummaryDTO result = statsService.getDangerousZoneOfWeek();

        assertNotNull(result);
        // Promedio de 1.2141, 1.2142, 1.2143 ≈ 1.2142
        assertEquals(1.2142, result.getLatitude(), 0.001, "Latitud promediada del centro de celda");
        // Promedio de -77.2811, -77.2812, -77.2813 ≈ -77.2812
        assertEquals(-77.2812, result.getLongitude(), 0.001, "Longitud promediada del centro de celda");
    }

    @Test
    @DisplayName("getDangerousZoneOfWeek retorna null cuando no hay reportes")
    void getDangerousZoneOfWeek_shouldReturnNullWhenEmpty() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DangerousZoneSummaryDTO result = statsService.getDangerousZoneOfWeek();

        assertNull(result, "Sin reportes, debería retornar null");
    }

    @Test
    @DisplayName("getDangerousZoneOfWeek incluye radio y label")
    void getDangerousZoneOfWeek_shouldIncludeRadiusAndLabel() {
        when(reportRepository.findRecentWithFullCoordinates(any(LocalDateTime.class)))
                .thenReturn(sampleReports);

        DangerousZoneSummaryDTO result = statsService.getDangerousZoneOfWeek();

        assertNotNull(result);
        assertEquals(100.0, result.getRadiusMeters(), "Radio estimado = 100m");
        assertNotNull(result.getLabel(), "Debería tener etiqueta descriptiva");
        assertTrue(result.getLabel().contains("ROBBERY"), "La etiqueta debería incluir el tipo");
    }
}
