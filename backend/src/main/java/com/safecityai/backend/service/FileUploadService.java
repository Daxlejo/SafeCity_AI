package com.safecityai.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    // Extensiones permitidas (solo imagenes)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif"
    );

    // Se ejecuta al iniciar la app: crea la carpeta uploads si no existe
    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }
    }

    /**
     * Guarda un archivo en el servidor y devuelve el nombre unico generado.
     * 
     * Flujo:
     * 1. Valida que no este vacio
     * 2. Valida que sea una imagen (jpg, png, webp, gif)
     * 3. Genera un nombre unico con UUID para evitar colisiones
     * 4. Guarda el archivo en la carpeta uploads/
     * 5. Devuelve el nombre del archivo guardado
     */
    public String uploadFile(MultipartFile file) {
        // Validar que no este vacio
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }

        // Obtener extension y validar
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Formato no permitido. Solo se aceptan: " + ALLOWED_EXTENSIONS);
        }

        // Generar nombre unico: uuid + extension original
        String uniqueName = UUID.randomUUID().toString() + "." + extension;

        try {
            // Guardar archivo
            Path targetPath = uploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return uniqueName;
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo", e);
        }
    }

    // Cargar un archivo guardado (para servirlo al frontend)
    public Path loadFile(String filename) {
        Path filePath = uploadPath.resolve(filename).normalize();
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Archivo no encontrado: " + filename);
        }
        return filePath;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
