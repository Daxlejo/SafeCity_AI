# Arquitectura del Sistema

## 1. Stack Tecnológico
- **Backend:** Java 21, Spring Boot 3.x, Spring Security (JWT), Hibernate/JPA.
- **Frontend:** React 18 (Vite), Leaflet 1.9, Axios, STOMPjs (WebSockets).
- **Infraestructura:** Supabase (PostgreSQL), Render (Hosting), Cloudinary (Imágenes).

## 2. Pipeline de IA Multimodal (Single Layer)
- **Modelo:** GPT-4o mini (vía API directa o OpenRouter).
- **Flujo de Decisión:**
    - Se elimina el motor de consenso de dos capas.
    - El reporte se envía a la IA en una sola petición multimodal (Texto + Imagen).
    - **Inyección de Contexto:** El prompt debe incluir el trustLevel actual del usuario para que la IA ajuste su criterio de evaluación.
- **Asincronía:** La clasificación debe ejecutarse mediante `@Async` tras el commit de la base de datos para evitar bloqueos y condiciones de carrera.

## 3. Sistema de Tiempo Real
- **WebSockets:** Uso de STOMP nativo para notificar nuevos reportes (`/topic/reports/ALL`) y actualizaciones de estatus tras el análisis de la IA.
