package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintConfigDTO;
import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.model.enums.OsintArticleStatus;
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
@Tag(name = "OSINT", description = "Operaciones de búsqueda y escaneo de incidentes en fuentes abiertas")
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
            description = "Busca incidentes en fuentes abiertas sin clasificarlos ni guardarlos.")
    public ResponseEntity<List<OsintResultDTO>> search(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.searchIncidents(city));
    }

    @PostMapping("/scan")
    @Operation(summary = "Escanear y clasificar incidentes (V2)",
            description = "Pipeline OSINT V2: scraping → extracción IA → geocodificación → dedup → reportes.")
    public ResponseEntity<Map<String, Object>> scanAndClassify(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.scanAndClassify(city));
    }

    // ═══════════════════════════════════════════
    // TRIGGER MANUAL (Admin)
    // ═══════════════════════════════════════════

    @PostMapping("/trigger")
    @Operation(summary = "Forzar escaneo OSINT manualmente",
            description = "Dispara un escaneo OSINT inmediato sin esperar al scheduler. Solo admins.")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        OsintConfigDTO config = osintConfigService.getConfigDTO();
        String city = config.getDefaultCity() != null ? config.getDefaultCity() : "Pasto";
        Map<String, Object> result = osintService.scanAndClassify(city);
        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════
    // NOTICIAS OSINT — Feed público
    // ═══════════════════════════════════════════

    @GetMapping("/news")
    @Operation(summary = "Noticias Pasto (Feed público)",
            description = "Lista paginada de noticias de seguridad detectadas por OSINT, visibles al público. Ordenadas de más reciente a más antiguo.")
    @ApiResponse(responseCode = "200", description = "Lista paginada de noticias publicadas")
    public ResponseEntity<Page<OsintNewsArticle>> getNewsArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                newsArticleRepository.findByStatusOrderByCreatedAtDesc(
                        OsintArticleStatus.PUBLISHED, PageRequest.of(page, size)));
    }

    // ═══════════════════════════════════════════
    // MODERACIÓN DE NOTICIAS (Solo Admin)
    // ═══════════════════════════════════════════

    @GetMapping("/news/all")
    @Operation(summary = "Todas las noticias OSINT (Admin)",
            description = "Lista paginada con TODOS los artículos (PUBLISHED + HIDDEN) para moderación.")
    public ResponseEntity<Page<OsintNewsArticle>> getAllNewsAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                newsArticleRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @PatchMapping("/news/{id}/status")
    @Operation(summary = "Cambiar visibilidad de noticia (Admin)",
            description = "Permite al admin publicar u ocultar un artículo del feed público sin eliminarlo.")
    public ResponseEntity<OsintNewsArticle> updateNewsStatus(
            @PathVariable Long id,
            @RequestParam OsintArticleStatus status) {
        return newsArticleRepository.findById(id)
                .map(article -> {
                    article.setStatus(status);
                    return ResponseEntity.ok(newsArticleRepository.save(article));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/news/{id}")
    @Operation(summary = "Eliminar noticia OSINT (Admin)",
            description = "Elimina permanentemente un artículo de la base de datos.")
    public ResponseEntity<Void> deleteNews(@PathVariable Long id) {
        if (!newsArticleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        newsArticleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════
    // CONFIGURACIÓN OSINT (Admin CRUD)
    // ═══════════════════════════════════════════

    @GetMapping("/config")
    @Operation(summary = "Obtener configuración OSINT")
    public ResponseEntity<OsintConfigDTO> getConfig() {
        return ResponseEntity.ok(osintConfigService.getConfigDTO());
    }

    @PutMapping("/config")
    @Operation(summary = "Actualizar configuración OSINT (Admin)")
    public ResponseEntity<OsintConfigDTO> updateConfig(@RequestBody OsintConfigDTO configDTO) {
        return ResponseEntity.ok(osintConfigService.updateConfig(configDTO));
    }

    @PutMapping("/config/toggle")
    @Operation(summary = "Activar/Desactivar scheduler OSINT (Admin)")
    public ResponseEntity<OsintConfigDTO> toggleOsint(@RequestParam boolean enabled) {
        return ResponseEntity.ok(osintConfigService.toggleEnabled(enabled));
    }
}
