package com.safecityai.backend.controller;

import com.safecityai.backend.dto.OsintResultDTO;
import com.safecityai.backend.service.OsintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/osint")
public class OsintController {

    private final OsintService osintService;

    public OsintController(OsintService osintService) {
        this.osintService = osintService;
    }

    // GET /api/v1/osint/search?city=Pasto → busca incidentes en fuentes abiertas
    @GetMapping("/search")
    public ResponseEntity<List<OsintResultDTO>> search(
            @RequestParam(defaultValue = "Pasto") String city) {
        return ResponseEntity.ok(osintService.searchIncidents(city));
    }
}
