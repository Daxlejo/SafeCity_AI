# Reglas de Negocio y Lógica de Datos

## 1. Gestión de Reportes
- **Visibilidad:** Los reportes con estatus `REJECTED` deben ser excluidos de las consultas públicas del mapa.
- **Geocodificación:** Si el usuario no proporciona dirección pero tiene GPS, el backend debe realizar Reverse Geocoding automáticamente antes de guardar.
- **Deduplicación OSINT:**
    - Cada noticia/post scrapeado debe generar un `descriptionHash` (SHA-256).
    - Antes de insertar, verificar si el hash ya existe para evitar reportes duplicados por la misma noticia.

## 2. Confianza y Penalizaciones
- **Trust Score:** El puntaje de confianza (0-100) lo asigna GPT-4o mini.
- **Impacto en Usuario:**
    - Reporte verificado: Incrementa el trustLevel del ciudadano.
    - Reporte rechazado (falso/broma): Penaliza el trustLevel.
- **Regla de Oro:** Un usuario con nivel `VERIFIED` tiene presunción de veracidad; la IA debe ser más flexible en el análisis de sus reportes.

## 3. Optimización de Recursos (Rate Limiting)
- **Filtro Heurístico:** Antes de llamar a la API de GPT, aplicar validación de contenido inválido (groserías, texto sin sentido, longitud mínima).
- **Límite Dinámico:** El `ReportService` debe validar que un usuario no exceda N reportes por hora, ajustando este límite según su trustLevel.
