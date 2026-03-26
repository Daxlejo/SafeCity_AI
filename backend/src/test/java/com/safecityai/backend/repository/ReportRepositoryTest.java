package com.safecityai.backend.repository;

import com.safecityai.backend.model.Report;
import com.safecityai.backend.model.enums.IncidentType;
import com.safecityai.backend.model.enums.ReportSource;
import com.safecityai.backend.model.enums.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de INTEGRACIÓN del repositorio.
 *
 * @DataJpaTest levanta SOLO la capa de persistencia (JPA + BD H2).
 * No levanta controllers, ni services, ni el servidor web completo.
 * Esto hace que los tests sean rápidos y enfocados en las queries SQL.
 *
 * @ActiveProfiles("test") carga application-test.properties.
 *
 * Cada test es @Transactional por defecto (cortesía de @DataJpaTest),
 * así que los datos insertados en un test se borran automáticamente
 * antes del siguiente → los tests son independientes entre sí.
 */
@DataJpaTest
@ActiveProfiles("test")
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    /*
     * Coordenadas de referencia para los tests:
     *
     * BOGOTÁ (centro):    lat=4.6097,  lng=-74.0817
     * REPORTE CERCANO:    lat=4.6150,  lng=-74.0750  (~0.9 km del centro)
     * REPORTE MEDIO:      lat=4.6300,  lng=-74.0600  (~3.1 km del centro)
     * REPORTE LEJANO:     lat=4.7100,  lng=-73.9500  (~16.5 km del centro)
     * REPORTE SIN COORDS: lat=null,    lng=null
     */

    private Report reporteCercano;
    private Report reporteMedio;
    private Report reporteLejano;
    private Report reporteSinCoords;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();

        reporteCercano = reportRepository.save(Report.builder()
                .description("Robo a mano armada cerca al centro")
                .incidentType(IncidentType.ROBBERY)
                .address("Calle 10 #5-30, Bogotá")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.PENDING)
                .latitude(4.6150)
                .longitude(-74.0750)
                .build());

        reporteMedio = reportRepository.save(Report.builder()
                .description("Accidente de tránsito en la autopista")
                .incidentType(IncidentType.ACCIDENT)
                .address("Autopista Norte Km 3, Bogotá")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.VERIFIED)
                .latitude(4.6300)
                .longitude(-74.0600)
                .build());

        reporteLejano = reportRepository.save(Report.builder()
                .description("Operativo de tránsito en la periferia")
                .incidentType(IncidentType.TRANSIT_OP)
                .address("Vía La Calera, Bogotá")
                .source(ReportSource.INSTITUTIONAL)
                .status(ReportStatus.PENDING)
                .latitude(4.7100)
                .longitude(-73.9500)
                .build());

        reporteSinCoords = reportRepository.save(Report.builder()
                .description("Hurto reportado sin ubicación GPS")
                .incidentType(IncidentType.OTHER)
                .address("Barrio Kennedy, Bogotá")
                .source(ReportSource.CITIZEN_VOICE)
                .status(ReportStatus.PENDING)
                .latitude(null)
                .longitude(null)
                .build());
    }

    // ══════════════════════════════════════════════════════
    //                    findNearby
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("findNearby - Búsqueda por radio (Haversine)")
    class FindNearbyTests {

        @Test
        @DisplayName("Radio de 1 km → solo encuentra el reporte cercano")
        void findNearby_1km_onlyClosest() {
            List<Report> results = reportRepository.findNearby(4.6097, -74.0817, 1.0);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(reporteCercano.getId());
        }

        @Test
        @DisplayName("Radio de 5 km → encuentra cercano y medio, excluye lejano")
        void findNearby_5km_closestAndMedium() {
            List<Report> results = reportRepository.findNearby(4.6097, -74.0817, 5.0);

            assertThat(results).hasSize(2);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).contains(reporteCercano.getId(), reporteMedio.getId());
            assertThat(ids).doesNotContain(reporteLejano.getId());
        }

        @Test
        @DisplayName("Radio de 20 km → encuentra los 3 con coordenadas, excluye el sin coords")
        void findNearby_20km_allWithCoordinates() {
            List<Report> results = reportRepository.findNearby(4.6097, -74.0817, 20.0);

            assertThat(results).hasSize(3);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }

        @Test
        @DisplayName("Radio de 0.1 km desde un punto sin reportes → lista vacía")
        void findNearby_noMatches_emptyList() {
            // Punto lejano: Medellín
            List<Report> results = reportRepository.findNearby(6.2442, -75.5812, 0.1);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("No incluye reportes con coordenadas nulas")
        void findNearby_excludesNullCoordinates() {
            List<Report> results = reportRepository.findNearby(4.6097, -74.0817, 100.0);

            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }
    }

    // ══════════════════════════════════════════════════════
    //                     findByZone
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByZone - Búsqueda por rectángulo (bounding box)")
    class FindByZoneTests {

        @Test
        @DisplayName("Zona pequeña alrededor del centro → solo cercano")
        void findByZone_smallBox_onlyClosest() {
            List<Report> results = reportRepository.findByZone(
                    4.610, 4.620,   // latMin, latMax
                    -74.080, -74.070  // lngMin, lngMax
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(reporteCercano.getId());
        }

        @Test
        @DisplayName("Zona mediana → cercano y medio")
        void findByZone_mediumBox_twoReports() {
            List<Report> results = reportRepository.findByZone(
                    4.610, 4.640,
                    -74.090, -74.050
            );

            assertThat(results).hasSize(2);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).contains(reporteCercano.getId(), reporteMedio.getId());
        }

        @Test
        @DisplayName("Zona grande → todos los que tienen coordenadas")
        void findByZone_largeBox_allWithCoordinates() {
            List<Report> results = reportRepository.findByZone(
                    4.50, 4.80,
                    -74.20, -73.90
            );

            assertThat(results).hasSize(3);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }

        @Test
        @DisplayName("Zona sin reportes → lista vacía")
        void findByZone_noMatches_emptyList() {
            // Zona en Medellín
            List<Report> results = reportRepository.findByZone(
                    6.20, 6.30,
                    -75.60, -75.50
            );

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("No incluye reportes con coordenadas nulas")
        void findByZone_excludesNullCoordinates() {
            List<Report> results = reportRepository.findByZone(
                    -90, 90,     // Toda la Tierra
                    -180, 180
            );

            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }
    }
}
