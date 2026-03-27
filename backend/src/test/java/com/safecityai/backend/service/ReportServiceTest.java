package com.safecityai.backend.service;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests UNITARIOS del servicio.
 *
 * @ExtendWith(MockitoExtension.class) permite usar @Mock e @InjectMocks
 * SIN levantar Spring. Esto hace que los tests sean MUY rápidos.
 *
 * @Mock → crea un objeto falso del ReportRepository
 * @InjectMocks → crea el ReportService inyectándole el mock
 *
 * En estos tests NO se ejecutan queries SQL reales.
 * Solo verificamos que el Service:
 * 1. Llama al Repository con los parámetros correctos
 * 2. Convierte las entidades a DTOs correctamente
 * 3. Valida los parámetros de entrada
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    /**
     * Helper: crea un Report de ejemplo para usar en los tests.
     */
    private Report buildSampleReport(Long id, double lat, double lng) {
        return Report.builder()
                .id(id)
                .description("Reporte de prueba #" + id)
                .incidentType(IncidentType.ROBBERY)
                .address("Dirección de prueba #" + id)
                .status(ReportStatus.PENDING)
                .source(ReportSource.CITIZEN_TEXT)
                .latitude(lat)
                .longitude(lng)
                .reportDate(LocalDateTime.of(2026, 3, 26, 10, 0))
                .build();
    }

    // ══════════════════════════════════════════════════════
    //                  findNearbyReports
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("findNearbyReports")
    class FindNearbyReportsTests {

        @Test
        @DisplayName("Devuelve DTOs correctamente mapeados desde entidades")
        void returnsCorrectDTOs() {
            // Arrange: configurar qué devuelve el mock
            Report r1 = buildSampleReport(1L, 1.2190, -77.2750);
            Report r2 = buildSampleReport(2L, 1.2350, -77.2600);
            when(reportRepository.findNearby(1.2136, -77.2811, 5.0))
                    .thenReturn(List.of(r1, r2));

            // Act: ejecutar el método
            List<ReportResponseDTO> result = reportService.findNearbyReports(1.2136, -77.2811, 5.0);

            // Assert: verificar resultado
            assertThat(result).hasSize(2);

            // Verificar que el mapeo Entity → DTO es correcto
            ReportResponseDTO dto1 = result.get(0);
            assertThat(dto1.getId()).isEqualTo(1L);
            assertThat(dto1.getDescription()).isEqualTo("Reporte de prueba #1");
            assertThat(dto1.getIncidentType()).isEqualTo(IncidentType.ROBBERY);
            assertThat(dto1.getLatitude()).isEqualTo(1.2190);
            assertThat(dto1.getLongitude()).isEqualTo(-77.2750);
            assertThat(dto1.getStatus()).isEqualTo(ReportStatus.PENDING);

            // Verificar que se llamó al repo exactamente 1 vez con los params correctos
            verify(reportRepository, times(1)).findNearby(1.2136, -77.2811, 5.0);
        }

        @Test
        @DisplayName("Devuelve lista vacía cuando no hay reportes cercanos")
        void returnsEmptyWhenNoMatches() {
            when(reportRepository.findNearby(6.2442, -75.5812, 1.0))
                    .thenReturn(Collections.emptyList());

            List<ReportResponseDTO> result = reportService.findNearbyReports(6.2442, -75.5812, 1.0);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Lanza excepción si el radio es 0 o negativo")
        void throwsExceptionForInvalidRadius() {
            assertThatThrownBy(() -> reportService.findNearbyReports(1.2136, -77.2811, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("radio");

            assertThatThrownBy(() -> reportService.findNearbyReports(1.2136, -77.2811, -5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("radio");
        }

        @Test
        @DisplayName("Lanza excepción si la latitud está fuera de rango")
        void throwsExceptionForInvalidLatitude() {
            assertThatThrownBy(() -> reportService.findNearbyReports(91, -77.2811, 5.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latitud");

            assertThatThrownBy(() -> reportService.findNearbyReports(-91, -77.2811, 5.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latitud");
        }

        @Test
        @DisplayName("Lanza excepción si la longitud está fuera de rango")
        void throwsExceptionForInvalidLongitude() {
            assertThatThrownBy(() -> reportService.findNearbyReports(1.2136, 181, 5.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitud");

            assertThatThrownBy(() -> reportService.findNearbyReports(1.2136, -181, 5.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitud");
        }
    }

    // ══════════════════════════════════════════════════════
    //                  findReportsByZone
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("findReportsByZone")
    class FindReportsByZoneTests {

        @Test
        @DisplayName("Devuelve DTOs correctamente mapeados desde entidades")
        void returnsCorrectDTOs() {
            Report r1 = buildSampleReport(1L, 1.2190, -77.2750);
            when(reportRepository.findByZone(1.2100, 1.2500, -77.3000, -77.2000))
                    .thenReturn(List.of(r1));

            List<ReportResponseDTO> result = reportService.findReportsByZone(1.2100, 1.2500, -77.3000, -77.2000);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getLatitude()).isEqualTo(1.2190);

            verify(reportRepository, times(1)).findByZone(1.2100, 1.2500, -77.3000, -77.2000);
        }

        @Test
        @DisplayName("Devuelve lista vacía cuando no hay reportes en la zona")
        void returnsEmptyWhenNoMatches() {
            when(reportRepository.findByZone(6.20, 6.30, -75.60, -75.50))
                    .thenReturn(Collections.emptyList());

            List<ReportResponseDTO> result = reportService.findReportsByZone(6.20, 6.30, -75.60, -75.50);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Lanza excepción si latMin > latMax")
        void throwsExceptionWhenLatMinGreaterThanMax() {
            assertThatThrownBy(() -> reportService.findReportsByZone(1.2500, 1.2100, -77.3000, -77.2000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latMin");
        }

        @Test
        @DisplayName("Lanza excepción si lngMin > lngMax")
        void throwsExceptionWhenLngMinGreaterThanMax() {
            assertThatThrownBy(() -> reportService.findReportsByZone(1.2100, 1.2500, -77.2000, -77.3000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lngMin");
        }

        @Test
        @DisplayName("Lanza excepción si coordenadas fuera de rango")
        void throwsExceptionForInvalidCoordinates() {
            assertThatThrownBy(() -> reportService.findReportsByZone(-91, 90, -180, 180))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> reportService.findReportsByZone(-90, 91, -180, 180))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
