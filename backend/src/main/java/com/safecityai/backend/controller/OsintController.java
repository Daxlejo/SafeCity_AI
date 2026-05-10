package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintConfigDTO;
import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.repository.OsintNewsArticleRepository;
import com.safecityai.backend.service.OsintConfigService;
import com.safecityai.backend.service.OsintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/osint")
@Tag(name = "OSINT", description = "Operaciones de búsqueda y escaneo de incidentes en fuentes abiertas (Redes Sociales, Noticias)")
public class OsintController {

    private final OsintService osintService;
    private final OsintNewsArticleRepository newsArticleRepository;
    private final OsintConfigService osintConfigService;

    public OsintController(OsintService osintService,
                           OsintNewsArticleRepository newsArticleRepository,
                           OsintConfigService osintConfigService) {
        this.osintService = osintService;
        this.newsArticleRepository = newsArticleRepository;
        this.osintConfigService = osintConfigService;
    }

    // ═══════════════════════════════════════════
    // BÚSQUEDA Y ESCANEO
    // ═══════════════════════════════════════════

    @GetMapping("/search")
    @Operation(summary = "Buscar incidentes (Preview)",
            description = "Busca incidentes en fuentes abiertas para una ciudad específica, sin clasificarlos ni guardarlos en la base de datos.")
    @ApiResponse(responseCode = "200", description = "Búsqueda completada exitosamente")
    public ResponseEntity<List<OsintResultDTO>> search(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.searchIncidents(city));
    }

    @PostMapping("/scan")
    @Operation(summary = "Escanear y clasificar incidentes (V2)",
            description = "Pipeline OSINT V2: scraping → extracción IA → geocodificación → deduplicación geoespacial → creación de reportes. Las noticias sin ubicación se guardan en 'Noticias Pasto'.")
    @ApiResponse(responseCode = "200", description = "Pipeline V2 ejecutado correctamente")
    public ResponseEntity<Map<String, Object>> scanAndClassify(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.scanAndClassify(city));
    }

    // ═══════════════════════════════════════════
    // TRIGGER MANUAL (Admin)
    // ═══════════════════════════════════════════

    @PostMapping("/trigger")
    @Operation(summary = "Forzar escaneo OSINT manualmente",
            description = "Dispara un escaneo OSINT inmediato sin esperar al scheduler. Solo disponible para administradores.")
    @ApiResponse(responseCode = "200", description = "Escaneo disparado correctamente")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        OsintConfigDTO config = osintConfigService.getConfigDTO();
        String city = config.getDefaultCity() != null ? config.getDefaultCity() : "Pasto";
        Map<String, Object> result = osintService.scanAndClassify(city);
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════
    // NOTICIAS OSINT
    // ═══════════════════════════════════════════

    @GetMapping("/news")
    @Operation(summary = "Noticias Pasto",
            description = "Lista de noticias de seguridad relevantes que no pudieron ser geolocalizadas. Estas noticias no aparecen en el mapa.")
    @ApiResponse(responseCode = "200", description = "Lista paginada de noticias")
    public ResponseEntity<Page<OsintNewsArticle>> getNewsArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                newsArticleRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    // ═══════════════════════════════════════════
    // CONFIGURACIÓN OSINT (Admin CRUD)
    // ═══════════════════════════════════════════

    @GetMapping("/config")
    @Operation(summary = "Obtener configuración OSINT",
            description = "Retorna la configuración actual del módulo OSINT: estado, intervalo, keywords, URLs prioritarias.")
    @ApiResponse(responseCode = "200", description = "Configuración obtenida correctamente")
    public ResponseEntity<OsintConfigDTO> getConfig() {
        return ResponseEntity.ok(osintConfigService.getConfigDTO());
    }

    @PutMapping("/config")
    @Operation(summary = "Actualizar configuración OSINT",
            description = "Actualiza la configuración global del módulo OSINT. Solo disponible para administradores.")
    @ApiResponse(responseCode = "200", description = "Configuración actualizada correctamente")
    public ResponseEntity<OsintConfigDTO> updateConfig(@RequestBody OsintConfigDTO configDTO) {
        return ResponseEntity.ok(osintConfigService.updateConfig(configDTO));
    }

    @PutMapping("/config/toggle")
    @Operation(summary = "Activar/Desactivar scheduler OSINT",
            description = "Cambia rápidamente el estado del scheduler OSINT sin modificar otras configuraciones.")
    @ApiResponse(responseCode = "200", description = "Estado del scheduler actualizado")
    public ResponseEntity<OsintConfigDTO> toggleOsint(@RequestParam boolean enabled) {
        return ResponseEntity.ok(osintConfigService.toggleEnabled(enabled));
    }
}
