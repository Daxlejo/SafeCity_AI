package com.safecityai.backend.controller;

import com.safecityai.backend.dto.ReportResponseDTO;
import com.safecityai.backend.dto.ZoneCreateDTO;
import com.safecityai.backend.dto.ZoneResponseDTO;
import com.safecityai.backend.model.enums.RiskLevel;
import com.safecityai.backend.service.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    // GET /api/v1/zones → todas las zonas
    @GetMapping
    public ResponseEntity<List<ZoneResponseDTO>> getAll() {
        return ResponseEntity.ok(zoneService.findAll());
    }

    // GET /api/v1/zones/{id} → detalle de una zona
    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.findById(id));
    }

    // GET /api/v1/zones/risky → solo zonas HIGH y CRITICAL
    @GetMapping("/risky")
    public ResponseEntity<List<ZoneResponseDTO>> getRisky() {
        return ResponseEntity.ok(zoneService.findRisky());
    }

    // GET /api/v1/zones/{id}/timeline → linea de tiempo de incidentes en la zona
    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<ReportResponseDTO>> getTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getZoneTimeline(id));
    }

    // POST /api/v1/zones → crear zona (requiere token ADMIN)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponseDTO> create(@Valid @RequestBody ZoneCreateDTO dto) {
        return new ResponseEntity<>(zoneService.create(dto), HttpStatus.CREATED);
    }

    // PUT /api/v1/zones/{id}/risk → actualizar nivel de riesgo (solo ADMIN)
    @PutMapping("/{id}/risk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneResponseDTO> updateRisk(@PathVariable Long id,
                                                       @RequestParam RiskLevel level) {
        return ResponseEntity.ok(zoneService.updateRiskLevel(id, level));
    }

    // DELETE /api/v1/zones/{id} → eliminar zona (solo ADMIN)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
