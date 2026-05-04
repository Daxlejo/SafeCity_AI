package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.repository.OsintNewsArticleRepository;
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

    public OsintController(OsintService osintService,
                           OsintNewsArticleRepository newsArticleRepository) {
        this.osintService = osintService;
        this.newsArticleRepository = newsArticleRepository;
    }

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
}
