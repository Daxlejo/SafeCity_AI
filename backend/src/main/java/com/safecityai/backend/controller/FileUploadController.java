package com.safecityai.backend.controller;

import com.safecityai.backend.service.FileUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * URL base del backend (ej. "https://safecity-ai-backend.onrender.com").
     * Se configura en application.properties como: app.base-url=${APP_BASE_URL:http://localhost:8080}
     * Permite construir la URL completa de la foto sin depender del frontend.
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    /**
     * POST /api/v1/uploads → sube una foto y devuelve la URL completa.
     *
     * El backend construye la URL absoluta usando app.base-url para evitar
     * que el frontend dependa de VITE_BACKEND_URL al renderizar la foto.
     *
     * Respuesta: { "fileName": "uuid.jpg", "photoUrl": "https://backend.../api/v1/uploads/uuid.jpg" }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String fileName = fileUploadService.uploadFile(file);
        // La URL completa permite que el frontend use photoUrl directamente en <img src=...>
        String fullPhotoUrl = baseUrl + "/api/v1/uploads/" + fileName;

        return ResponseEntity.ok(Map.of(
                "fileName", fileName,
                "photoUrl", fullPhotoUrl
        ));
    }

    /**
     * GET /api/v1/uploads/{filename} → sirve la foto
     * 
     * El frontend usa esta URL como src de un <img>.
     * La foto se muestra directamente en la app sin abrir ningun link.
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = fileUploadService.loadFile(filename);
            Resource resource = new UrlResource(filePath.toUri());

            // Detectar el tipo de contenido (image/jpeg, image/png, etc)
            String contentType = "image/jpeg";
            if (filename.endsWith(".png")) contentType = "image/png";
            else if (filename.endsWith(".webp")) contentType = "image/webp";
            else if (filename.endsWith(".gif")) contentType = "image/gif";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
