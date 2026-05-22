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
}
