package com.safecityai.backend.controller;

import com.safecityai.backend.service.FileUploadService;
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

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    /**
     * POST /api/v1/uploads → sube una foto
     * 
     * Devuelve solo el fileName. El frontend construye la URL completa
     * dinámicamente con VITE_BACKEND_URL para evitar URLs rotas tras redeploys.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String fileName = fileUploadService.uploadFile(file);

        return ResponseEntity.ok(Map.of(
                "fileName", fileName,
                "photoUrl", fileName
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
