package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.service.OsintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/osint")
public class OsintController {

    private final OsintService osintService;

    public OsintController(OsintService osintService) {
        this.osintService = osintService;
    }

    // GET /api/v1/osint/search?city=Pasto
    @GetMapping("/search")
    public ResponseEntity<List<OsintResultDTO>> search(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.searchIncidents(city));
    }

    /**
     * POST /api/v1/osint/scan?city=Pasto
     * Flujo:
     * 1. Busca en Google News + Facebook (paginas prioritarias + general)
     * 2. Crea un reporte en la BD por cada resultado
     * 3. Clasifica cada reporte con Gemini AI
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanAndClassify(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.scanAndClassify(city));
    }
}
