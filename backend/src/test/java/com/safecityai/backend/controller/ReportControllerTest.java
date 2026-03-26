package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import com.safecityai.backend.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de INTEGRACIÓN del controller.
 *
 * @WebMvcTest levanta SOLO la capa web (controller + filtros + serialización JSON).
 * No levanta el servidor completo ni la BD.
 *
 * MockMvc simula peticiones HTTP sin necesidad de un servidor real.
 * El ReportService se mockea para aislar la capa del controller.
 *
 * Nota: @WebMvcTest NO carga el server.servlet.context-path (/api),
 * por lo que las URLs en los tests son directamente /reports/nearby
 * en lugar de /api/reports/nearby.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    /**
     * Helper: crea un DTO de ejemplo.
     */
    private ReportResponseDTO buildSampleDTO(Long id, double lat, double lng) {
        return ReportResponseDTO.builder()
                .id(id)
                .description("Reporte de prueba #" + id)
                .incidentType(IncidentType.ROBBERY)
                .address("Dirección #" + id)
                .status(ReportStatus.PENDING)
                .source(ReportSource.CITIZEN_TEXT)
                .latitude(lat)
                .longitude(lng)
                .reportDate(LocalDateTime.of(2026, 3, 26, 10, 0))
                .build();
    }

    // ══════════════════════════════════════════════════════
    //              GET /reports/nearby
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /reports/nearby")
    class NearbyEndpointTests {

        @Test
        @DisplayName("200 OK con reportes cercanos")
        void nearby_returnsReports() throws Exception {
            ReportResponseDTO dto1 = buildSampleDTO(1L, 4.6150, -74.0750);
            ReportResponseDTO dto2 = buildSampleDTO(2L, 4.6300, -74.0600);

            when(reportService.findNearbyReports(4.6097, -74.0817, 5.0))
                    .thenReturn(List.of(dto1, dto2));

            mockMvc.perform(get("/api/v1/reports/nearby")
                            .param("lat", "4.6097")
                            .param("lng", "-74.0817")
                            .param("radius", "5.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].latitude").value(4.6150))
                    .andExpect(jsonPath("$[0].incidentType").value("ROBBERY"))
                    .andExpect(jsonPath("$[1].id").value(2));
        }

        @Test
        @DisplayName("200 OK con lista vacía si no hay resultados")
        void nearby_returnsEmptyList() throws Exception {
            when(reportService.findNearbyReports(6.2442, -75.5812, 1.0))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/reports/nearby")
                            .param("lat", "6.2442")
                            .param("lng", "-75.5812")
                            .param("radius", "1.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("400 Bad Request si falta parámetro 'lat'")
        void nearby_missingLat_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/nearby")
                            .param("lng", "-74.0817")
                            .param("radius", "5.0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request si falta parámetro 'lng'")
        void nearby_missingLng_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/nearby")
                            .param("lat", "4.6097")
                            .param("radius", "5.0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request si falta parámetro 'radius'")
        void nearby_missingRadius_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/nearby")
                            .param("lat", "4.6097")
                            .param("lng", "-74.0817"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════
    //              GET /reports/zone
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /reports/zone")
    class ZoneEndpointTests {

        @Test
        @DisplayName("200 OK con reportes en la zona")
        void zone_returnsReports() throws Exception {
            ReportResponseDTO dto1 = buildSampleDTO(1L, 4.6150, -74.0750);

            when(reportService.findReportsByZone(4.60, 4.65, -74.10, -74.05))
                    .thenReturn(List.of(dto1));

            mockMvc.perform(get("/api/v1/reports/zone")
                            .param("latMin", "4.60")
                            .param("latMax", "4.65")
                            .param("lngMin", "-74.10")
                            .param("lngMax", "-74.05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].latitude").value(4.6150));
        }

        @Test
        @DisplayName("200 OK con lista vacía si no hay resultados")
        void zone_returnsEmptyList() throws Exception {
            when(reportService.findReportsByZone(6.20, 6.30, -75.60, -75.50))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/reports/zone")
                            .param("latMin", "6.20")
                            .param("latMax", "6.30")
                            .param("lngMin", "-75.60")
                            .param("lngMax", "-75.50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("400 Bad Request si falta parámetro 'latMin'")
        void zone_missingLatMin_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/zone")
                            .param("latMax", "4.65")
                            .param("lngMin", "-74.10")
                            .param("lngMax", "-74.05"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 Bad Request si falta parámetro 'lngMax'")
        void zone_missingLngMax_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/zone")
                            .param("latMin", "4.60")
                            .param("latMax", "4.65")
                            .param("lngMin", "-74.10"))
                    .andExpect(status().isBadRequest());
        }
    }
}
