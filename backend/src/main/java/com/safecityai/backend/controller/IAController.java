package com.safecityai.backend.controller;

import com.safecityai.backend.dto.IAClassificationDTO;
import com.safecityai.backend.service.IAClassificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ia")
public class IAController {

    private final IAClassificationService iaService;

    public IAController(IAClassificationService iaService) {
        this.iaService = iaService;
    }

    // POST /api/v1/ia/classify/{reportId} → la IA analiza un reporte
    @PostMapping("/classify/{reportId}")
    public ResponseEntity<IAClassificationDTO> classify(@PathVariable Long reportId) {
        return ResponseEntity.ok(iaService.classifyReport(reportId));
    }

    // POST /api/v1/ia/analyze → Recibe texto y sugiere IncidentType
    @PostMapping("/analyze")
    public ResponseEntity<java.util.Map<String, Object>> analyzeText(@RequestBody java.util.Map<String, String> body) {
        String description = body.get("description");
        com.safecityai.backend.model.enums.IncidentType suggestedType = iaService.suggestType(description);
        
        return ResponseEntity.ok(java.util.Map.of(
            "suggestedType", suggestedType,
            "confidence", suggestedType != com.safecityai.backend.model.enums.IncidentType.OTHER ? 0.85 : 0.40
        ));
    }

    // GET /api/v1/ia/keywords → Obtiene las keywords de clasificación
    @GetMapping("/keywords")
    public ResponseEntity<java.util.Map<String, java.util.List<String>>> getKeywords() {
        return ResponseEntity.ok(iaService.getKeywords());
    }
}
