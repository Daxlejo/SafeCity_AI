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
     * PASTO (centro):     lat=1.2136,  lng=-77.2811
     * REPORTE CERCANO:    lat=1.2190,  lng=-77.2750  (~0.9 km del centro)
     * REPORTE MEDIO:      lat=1.2350,  lng=-77.2600  (~3.3 km del centro)
     * REPORTE LEJANO:     lat=1.3200,  lng=-77.1800  (~16.2 km del centro)
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
                .address("Calle 18 #25-30, Pasto")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.PENDING)
                .latitude(1.2190)
                .longitude(-77.2750)
                .build());

        reporteMedio = reportRepository.save(Report.builder()
                .description("Accidente de tránsito en la zona universitaria")
                .incidentType(IncidentType.ACCIDENT)
                .address("Calle 18 cerca a la U Nariño, Pasto")
                .source(ReportSource.CITIZEN_TEXT)
                .status(ReportStatus.VERIFIED)
                .latitude(1.2350)
                .longitude(-77.2600)
                .build());

        reporteLejano = reportRepository.save(Report.builder()
                .description("Operativo de tránsito en la periferia")
                .incidentType(IncidentType.TRANSIT_OP)
                .address("Vía al Volcán Galeras, Pasto")
                .source(ReportSource.INSTITUTIONAL)
                .status(ReportStatus.PENDING)
                .latitude(1.3200)
                .longitude(-77.1800)
                .build());

        reporteSinCoords = reportRepository.save(Report.builder()
                .description("Hurto reportado sin ubicación GPS")
                .incidentType(IncidentType.OTHER)
                .address("Barrio Chapal, Pasto")
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
            List<Report> results = reportRepository.findNearby(1.2136, -77.2811, 1.0);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(reporteCercano.getId());
        }

        @Test
        @DisplayName("Radio de 5 km → encuentra cercano y medio, excluye lejano")
        void findNearby_5km_closestAndMedium() {
            List<Report> results = reportRepository.findNearby(1.2136, -77.2811, 5.0);

            assertThat(results).hasSize(2);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).contains(reporteCercano.getId(), reporteMedio.getId());
            assertThat(ids).doesNotContain(reporteLejano.getId());
        }

        @Test
        @DisplayName("Radio de 20 km → encuentra los 3 con coordenadas, excluye el sin coords")
        void findNearby_20km_allWithCoordinates() {
            List<Report> results = reportRepository.findNearby(1.2136, -77.2811, 20.0);

            assertThat(results).hasSize(3);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }

        @Test
        @DisplayName("Radio de 0.1 km desde un punto sin reportes → lista vacía")
        void findNearby_noMatches_emptyList() {
            // Punto lejano: Bogotá
            List<Report> results = reportRepository.findNearby(4.6097, -74.0817, 0.1);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("No incluye reportes con coordenadas nulas")
        void findNearby_excludesNullCoordinates() {
            List<Report> results = reportRepository.findNearby(1.2136, -77.2811, 100.0);

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
                    1.2100, 1.2200,   // latMin, latMax
                    -77.2800, -77.2700  // lngMin, lngMax
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(reporteCercano.getId());
        }

        @Test
        @DisplayName("Zona mediana → cercano y medio")
        void findByZone_mediumBox_twoReports() {
            List<Report> results = reportRepository.findByZone(
                    1.2100, 1.2400,
                    -77.2900, -77.2500
            );

            assertThat(results).hasSize(2);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).contains(reporteCercano.getId(), reporteMedio.getId());
        }

        @Test
        @DisplayName("Zona grande → todos los que tienen coordenadas")
        void findByZone_largeBox_allWithCoordinates() {
            List<Report> results = reportRepository.findByZone(
                    1.2000, 1.4000,
                    -77.3000, -77.1000
            );

            assertThat(results).hasSize(3);
            List<Long> ids = results.stream().map(Report::getId).toList();
            assertThat(ids).doesNotContain(reporteSinCoords.getId());
        }

        @Test
        @DisplayName("Zona sin reportes → lista vacía")
        void findByZone_noMatches_emptyList() {
            // Zona en Bogotá
            List<Report> results = reportRepository.findByZone(
                    4.6000, 4.6200,
                    -74.0900, -74.0800
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
