package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ZoneCreateDTO;
import com.safecityai.backend.dto.ZoneResponseDTO;
import com.safecityai.backend.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    // GET /api/v1/zones → listar todas las zonas (público)
    @GetMapping
    public ResponseEntity<List<ZoneResponseDTO>> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    // GET /api/v1/zones/{id} → detalle de una zona (público)
    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponseDTO> getZoneById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    // GET /api/v1/zones/risky → zonas de riesgo HIGH y CRITICAL (público)
    @GetMapping("/risky")
    public ResponseEntity<List<ZoneResponseDTO>> getRiskyZones() {
        return ResponseEntity.ok(zoneService.getRiskyZones());
    }

    // POST /api/v1/zones → crear zona (SOLO ADMIN → CITIZEN recibe 403)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponseDTO> createZone(@Valid @RequestBody ZoneCreateDTO dto) {
        ZoneResponseDTO response = zoneService.createZone(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
