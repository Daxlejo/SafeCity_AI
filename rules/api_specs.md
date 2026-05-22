# Especificaciones de API

## 1. Reportes y Estados de IA
- **Estado de Procesamiento:** Al crear un reporte (`POST /api/v1/reports/`), la respuesta debe incluir un flag `isProcessingIA: true`.
- **Websocket Sync:** El frontend debe escuchar el tópico `/topic/reports/updated` para recibir el resultado final del análisis de la IA y actualizar el marcador en el mapa sin recargar.

## 2. Módulo OSINT
- **Endpoint:** `POST /api/v1/osint/scan`.
- **Comportamiento:** Debe ser **asíncrono**. Retorna HTTP 202 (Accepted) de inmediato. El procesamiento de scraping, deduplicación y clasificación se realiza en segundo plano.

## 3. Seguridad
- **JWT:** Todas las peticiones (excepto GET de reportes públicos y Auth) deben incluir el Header `Authorization: Bearer <token>`.
- **Roles:**
    - `ROLE_CITIZEN`: Crear reportes, ver perfil, recibir alertas.
    - `ROLE_ADMIN`: Gestionar usuarios, banear, cambiar estatus de reportes, ver dashboard de estadísticas.
