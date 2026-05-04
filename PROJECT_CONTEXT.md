# SafeCity AI — Contexto Completo del Proyecto

> **Última actualización:** 2 Mayo 2026
> **Autor principal:** David Alejandro (Backend/IA Lead)
> **Equipo:** David, Cristian Yela (Backend), Edgar (Frontend)
> **Tipo:** Proyecto universitario con estándares de producción

---

## 1. ¿Qué es SafeCity AI?

Plataforma web de seguridad ciudadana para **Pasto, Colombia** que permite a los ciudadanos reportar incidentes de seguridad (robos, accidentes, tráfico) en un mapa interactivo. Los reportes son clasificados automáticamente por IA (OpenRouter actualmente muy pronto GPT 4o mini) que asigna un **trust score** (0-100) y puede verificar, rechazar o escalar a revisión humana.

### Propuesta de valor
- Reportes ciudadanos geolocalizados en tiempo real
- IA que filtra reportes falsos/broma y verifica los legítimos
- Dashboard administrativo con estadísticas y heatmap
- Notificaciones en tiempo real vía WebSocket
- OSINT: scraping de noticias para detectar incidentes automáticamente y generar reportes de manera automatica.

---

## 2. Tech Stack

### Backend
| Tecnología | Versión | Uso |
|---|---|---|
| Java | 21 | Lenguaje principal |
| Spring Boot | 3.x | Framework web + security + data |
| Spring Security | 6.x | JWT stateless auth |
| Spring Data JPA | 3.x | ORM con Hibernate |
| PostgreSQL | 15+ | Base de datos (Supabase en producción) |
| Spring WebSocket | STOMP | Tiempo real |
| Lombok | latest | Reducir boilerplate |
| SpringDoc OpenAPI | 2.x | Swagger UI automático |
| Maven | Wrapper | Build system |

### Frontend
| Tecnología | Versión | Uso |
|---|---|---|
| React | 18 | UI framework |
| Vite | 5.x | Bundler/dev server |
| Leaflet | 1.9 | Mapas interactivos |
| Axios | 1.x | HTTP client |
| @stomp/stompjs | 7.x | WebSocket STOMP |
| Lucide React | latest | Iconos |
| Vanilla CSS | - | Estilos (NO Tailwind) |

### Servicios Externos
| Servicio | Uso |
|---|---|
| OpenRouter API | Clasificación IA (modelos gratuitos) |
| Supabase | PostgreSQL en producción |
| Render | Hosting backend |
| Vercel/Netlify | Hosting frontend |
| Gmail SMTP | Correos de recuperación de contraseña |
| RapidAPI | Facebook scraper para OSINT |

---

## 3. Repositorios Git

| Repo | URL | Branch principal |
|---|---|---|
| Backend (monorepo) | `github.com/Daxlejo/SafeCity_AI` | `develop` |
| Frontend (review) | `github.com/Daxlejo/SafeCity_AI_FRONTEND` | `develop` |

**Nota:** Existen DOS carpetas frontend en el monorepo:
- `frontend_src/` — Frontend PRINCIPAL (tiene App.jsx, MapView.jsx, ReportDetailModal.jsx)
- `frontend_review/` — Frontend de Edgar (review/parcial, NO tiene App.jsx ni MapView.jsx)

El frontend desplegado es `frontend_src/`. Las páginas de Edgar (ProfileView, etc.) se copian a `frontend_src/`.

---

## 4. Estructura de Archivos

### Backend (`backend/src/main/java/com/safecityai/backend/`)

```
├── SafeCityBackendApplication.java          # Entry point
├── config/
│   ├── AsyncConfig.java                     # ThreadPool para @Async
│   ├── CorsConfig.java                      # CORS origins desde env vars
│   ├── DataInitializer.java                 # Seed admin user al iniciar
│   ├── SwaggerConfig.java                   # OpenAPI/Swagger UI
│   └── WebSocketConfig.java                 # STOMP endpoints /ws, /ws-sockjs
├── controller/
│   ├── AuthController.java                  # POST /auth/login, /register, /forgot-password, /reset-password
│   ├── ReportController.java                # CRUD /reports (Swagger anotado)
│   ├── AdminController.java                 # /admin/users, /admin/reports/{id}/status
│   ├── StatsController.java                 # /stats/summary, /by-type, /heatmap, /dangerous-zones
│   ├── NotificationController.java          # /notifications, /unread, /count, /{id}/read
│   ├── AlertController.java                 # /alerts/preferences CRUD
│   ├── OsintController.java                 # POST /osint/scan, GET /osint/results
│   ├── FileUploadController.java            # POST /uploads, GET /uploads/{filename}
│   ├── UserController.java                  # GET/PUT /users/me, PUT /users/me/password
│   ├── ZoneController.java                  # CRUD /zones
│   ├── IAController.java                    # POST /ia/classify/{reportId}
│   └── HealthController.java               # GET /health
├── dto/                                     # 20 DTOs (Request/Response)
│   ├── ReportCreateDTO.java                 # Validaciones: @NotBlank description, @NotNull incidentType
│   ├── ReportResponseDTO.java               # Incluye trustScore, aiAnalysis, status
│   ├── IAClassificationDTO.java             # trustScore, trustLevel, suggestedType, reasoning, shouldVerify
│   ├── UserRegisterDTO.java                 # email, cedula, password, name
│   ├── AuthResponseDTO.java                 # token, user info
│   ├── StatsSummaryDTO.java                 # totalReports, verified, pending, rejected counts
│   └── ... (16 más)
├── model/
│   ├── User.java                            # id, email, cedula, passwordHash, name, role, trustLevel, active, resetToken
│   ├── Report.java                          # id, description, incidentType, address, lat/lng, photoUrl, trustScore, aiAnalysis, status, source
│   ├── Notification.java                    # id, userId, message, read, type, createdAt
│   ├── Zone.java                            # id, name, lat/lng/radius, riskLevel
│   ├── AlertPreference.java                 # id, userId, incidentType, active
│   └── enums/
│       ├── IncidentType.java                # ROBBERY, ACCIDENT, TRAFFIC, TRANSIT_OP, OTHER
│       ├── ReportStatus.java                # PENDING, VERIFIED, REJECTED, RESOLVED
│       ├── UserRole.java                    # CITIZEN, ADMIN, MODERATOR
│       ├── TrustLevel.java                  # UNTRUSTED, LOW, MODERATE, HIGH, VERIFIED
│       ├── ReportSource.java                # CITIZEN_TEXT, OSINT_GOOGLE, OSINT_FACEBOOK, etc.
│       └── RiskLevel.java                   # LOW, MEDIUM, HIGH, CRITICAL
├── repository/                              # 5 JPA repositories con custom queries
│   ├── ReportRepository.java                # findAverageTrustScoreByUser, countByStatus, heatmap queries
│   └── ...
├── security/
│   ├── SecurityConfig.java                  # FilterChain: public/auth/admin endpoints
│   ├── JwtService.java                      # Genera/valida JWT con io.jsonwebtoken
│   └── JwtAuthenticationFilter.java         # Extrae email del JWT, carga SecurityContext
├── service/                                 # ★ LÓGICA DE NEGOCIO ★
│   ├── IAClassificationService.java         # Orquestador: pipeline secuencial Capa1→Capa2
│   ├── OpenRouterClient.java                # HTTP + retry + backoff + caché SHA-256
│   ├── ReportDecisionEngine.java            # Motor de consenso: needsSecondOpinion + applyConsensus
│   ├── ReportService.java                   # CRUD + classifyAsync post-commit
│   ├── UserService.java                     # Auth + perfil + forgot/reset password
│   ├── StatsService.java                    # Queries de estadísticas
│   ├── OsintService.java                    # Scraping Google News + Facebook
│   ├── GeocodingService.java                # Reverse geocoding (coordenadas → dirección)
│   ├── NotificationService.java             # WebSocket broadcast
│   ├── NotificationUserService.java         # CRUD notificaciones persistentes
│   ├── FileUploadService.java               # Guardar fotos en disco
│   ├── AlertService.java                    # Preferencias de alertas
│   ├── EmailService.java                    # JavaMailSender para reset password
│   └── ZoneService.java                     # CRUD zonas
└── websocket/                               # (Config ya está en config/)
```

### Frontend (`frontend_src/src/`)

```
├── App.jsx                                  # Layout principal (Desktop sidebar + Mobile bottom sheet)
├── main.jsx                                 # Entry point con AuthProvider + ThemeProvider
├── components/
│   └── ReportDetailModal.jsx                # Modal glassmorphism con mini-mapa Leaflet
├── context/
│   ├── AuthContext.jsx                      # JWT state + login/logout/register
│   └── ThemeContext.jsx                     # Dark/light mode toggle
├── hooks/
│   └── useGeolocation.js                    # GPS custom hook con localStorage cache
├── pages/
│   ├── MapView.jsx                          # Mapa Leaflet + sidebar de reportes + formulario
│   ├── DashboardView.jsx                    # Stats: gráficas de barras, timeline, heatmap
│   ├── LoginPage.jsx                        # Login + Register + Forgot/Reset password
│   ├── AdminView.jsx                        # Gestión usuarios (ban/role) + moderación reportes
│   ├── NotificationsView.jsx                # Lista de notificaciones con mark as read
│   └── ProfileView.jsx                      # Ver/editar perfil + cambiar contraseña
├── services/
│   ├── api.js                               # Axios instance + interceptors + todos los endpoints
│   └── websocket.js                         # STOMP client: /topic/reports/ALL, /updated, /deleted
└── styles/
    └── index.css                             # ~2000 líneas: dark/light theme, glassmorphism, responsive
```

---

## 5. Modelos de Datos (Entidades JPA)

### User
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Auto-generated |
| email | String | Unique, login identifier |
| cedula | String(11) | Unique, cédula colombiana |
| passwordHash | String | BCrypt encoded |
| name | String(100) | Display name |
| role | UserRole | CITIZEN (default), ADMIN, MODERATOR |
| trustLevel | Double | 0-100, starts at 50. IA adjusts: -5 on rejected, +2 on verified |
| active | Boolean | Soft delete flag |
| resetToken | String | UUID para reset password (30 min expiry) |
| createdAt | LocalDateTime | Auto-generated |

### Report
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Auto-generated |
| description | String(500) | Texto del ciudadano |
| incidentType | IncidentType | ROBBERY, ACCIDENT, TRAFFIC, TRANSIT_OP, OTHER |
| address | String | Auto-generada por geocoding inverso si hay GPS |
| latitude/longitude | Double | Coordenadas GPS (nullable) |
| photoUrl | String(500) | URL de foto subida |
| status | ReportStatus | PENDING → VERIFIED/REJECTED/RESOLVED |
| source | ReportSource | CITIZEN_TEXT, OSINT_GOOGLE, OSINT_FACEBOOK |
| trustScore | Double | 0-100, asignado por la IA |
| aiAnalysis | String(2000) | Razonamiento de la IA |
| descriptionHash | String | SHA-256 para deduplicación OSINT |
| reportedBy | User (FK) | Relación ManyToOne |
| reportDate | LocalDateTime | Auto-generated |
| incidentDate | LocalDateTime | Fecha/hora del incidente (reportada por usuario) |

---

## 6. Pipeline de IA (Arquitectura SOLID)

### Flujo Secuencial
```
Reporte nuevo
    │
    ▼
[ReportService.createReport()]
    │ Guarda en BD
    │ TransactionSynchronization.afterCommit() ← fix race condition
    ▼
[IAClassificationService.classifyAsync()] ← @Async
    │
    ├── Pre-filtro heurístico (detectInvalidContent)
    │   Rechaza: leyendas, groserías, exageraciones, XD/LOL, etc.
    │
    ▼
[classifyWithAI(report)]
    │
    ├── Capa 1: OpenRouterClient.classify(prompt, NVIDIA_NEMOTRON)
    │   └── Caché SHA-256 → si ya se clasificó, retorna inmediato
    │   └── Retry x3 con backoff exponencial (2s, 4s)
    │
    ├── ReportDecisionEngine.needsSecondOpinion(gemmaResult)
    │   ├── Score ≥ 85 → ALTA confianza → retornar SIN Capa 2
    │   ├── Score ≤ 20 → rechazo claro → retornar SIN Capa 2
    │   └── Score 21-84 → zona gris → llamar Capa 2
    │
    ├── Capa 2: OpenRouterClient.classify(prompt, OPENAI_GPT_OSS)
    │
    └── ReportDecisionEngine.applyConsensus(capa1, capa2)
        ├── Regla 1: Ambos rechazan → RECHAZADO (score=0)
        ├── Regla 2: Discrepancia > 30pts → PENDING (revisión humana)
        └── Regla 3: Consenso → score = min(capa1, capa2)
```



### Clases del Pipeline (SOLID)
| Clase | Responsabilidad (SRP) |
|---|---|
| `OpenRouterClient` | HTTP + retry + backoff + caché SHA-256 (infraestructura) |
| `ReportDecisionEngine` | Reglas de consenso + needsSecondOpinion (lógica de negocio) |
| `IAClassificationService` | Orquestador: coordina flujo secuencial + heurísticas + notificaciones |

---

## 7. API Endpoints

### Auth (`/api/v1/auth/`) — Público
| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/register` | Registro con email, cédula, password, name |
| POST | `/login` | Login → retorna JWT + user info |
| POST | `/forgot-password` | Envía email con token de reset |
| POST | `/reset-password` | Valida token + nueva contraseña |

### Reports (`/api/v1/reports/`) — GET público, POST/PUT/DELETE autenticado
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/` | Lista paginada (excluye REJECTED). Params: page, size, sort, direction |
| GET | `/{id}` | Detalle de un reporte |
| POST | `/` | Crear reporte → IA clasifica automáticamente |
| PUT | `/{id}` | Actualizar reporte |
| DELETE | `/{id}` | Eliminar reporte (emite WebSocket) |

### Admin (`/api/v1/admin/`) — Solo ROLE_ADMIN
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/users` | Lista paginada de usuarios |
| PUT | `/users/{id}/role?role=ADMIN` | Cambiar rol |
| PUT | `/users/{id}/ban` | Toggle ban (active=false) |
| DELETE | `/users/{id}` | Eliminar usuario |
| PUT | `/reports/{id}/status?status=VERIFIED` | Cambiar status de reporte |

### Stats (`/api/v1/stats/`) — Solo ROLE_ADMIN
| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/summary` | Total reports, verified, pending, rejected |
| GET | `/by-type` | Conteo por IncidentType |
| GET | `/heatmap` | Puntos lat/lng para heatmap |
| GET | `/dangerous-zones?days=7` | Zonas más peligrosas |
| GET | `/timeline?limit=10` | Reportes por día |

### Otros
| Ruta | Descripción |
|---|---|
| `/api/v1/notifications/` | CRUD notificaciones (autenticado) |
| `/api/v1/alerts/preferences` | Preferencias de alertas (autenticado) |
| `/api/v1/osint/scan` | Trigger scan OSINT (público) |
| `/api/v1/uploads` | Upload/download fotos |
| `/api/v1/users/me` | Perfil del usuario autenticado |
| `/api/v1/zones/` | CRUD zonas (GET público, POST/PUT/DELETE admin) |
| `/ws` | WebSocket STOMP nativo |

---

## 8. WebSocket (Tiempo Real)

### Topics STOMP
| Topic | Evento | Payload |
|---|---|---|
| `/topic/reports/ALL` | Reporte nuevo creado | `ReportResponseDTO` (JSON) |
| `/topic/reports/updated` | Reporte actualizado (IA cambió status) | `ReportResponseDTO` (JSON) |
| `/topic/reports/deleted` | Reporte eliminado | `reportId` (number) |

### Flujo
1. Frontend se conecta a `wss://backend-url/ws` con STOMP
2. Se subscribe a los 3 topics
3. Backend emite eventos desde `NotificationService.broadcastReport()`
4. Frontend actualiza el array de reportes en tiempo real (sin refresh)

---

## 9. Frontend — Patrones de Diseño

### Layout Dual (Desktop + Mobile)
- **Desktop:** Sidebar izquierda (colapsable) + Main content derecha
- **Mobile:** Mapa fullscreen + Bottom Sheet swipeable (3 snap points: peek/half/full) + Bottom Navigation

### Estado Global
- `AuthContext`: JWT token, user object, login/logout
- `ThemeContext`: dark/light mode, persiste en localStorage
- Estado de reportes: lifted a `App.jsx`, pasado como props a `MapView`

### Custom Hooks
- `useGeolocation`: GPS con cache localStorage (10 min), detección mobile, retry, error messages
- `useIsMobile`: Media query reactivo con resize listener

### Estilos
- Vanilla CSS con custom properties (CSS variables)
- Dos temas: `[data-theme="dark"]` y `[data-theme="light"]`
- Glassmorphism: `backdrop-filter: blur()` + `rgba()` backgrounds
- Responsive: breakpoint `768px`

---

## 10. Variables de Entorno

### Backend (`application.properties` lee de env vars)
| Variable | Default | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Profile activo |
| `PORT` | `8080` | Puerto del servidor |
| `OPENROUTER_API_KEY` | (vacío) | **REQUERIDA** — API key de OpenRouter |
| `OPENROUTER_MODEL` | `openai/gpt-oss-120b:free` | Modelo fallback |
| `CORS_ALLOWED_ORIGINS` | `localhost:3000,5173` | CORS origins |
| `UPLOAD_DIR` | `uploads` | Directorio de fotos |
| `RAPIDAPI_KEY` | (vacío) | Key para Facebook scraper |
| `MAIL_USERNAME` | (vacío) | Gmail para reset password |
| `MAIL_PASSWORD` | (vacío) | App password de Gmail |

### Frontend (`.env` Vite)
| Variable | Default | Descripción |
|---|---|---|
| `VITE_BACKEND_URL` | `https://safecity-ai-backend.onrender.com` | URL del backend |

---

## 11. Seguridad

### Autenticación
- JWT stateless con `io.jsonwebtoken`
- Token incluye: email, role, userId
- Expiración: configurable en `JwtService`
- Password: BCrypt hash

### Autorización (SecurityConfig)
- **Público:** auth/*, health, swagger, GET reports, GET zones, osint/*, GET uploads
- **Autenticado:** POST/PUT/DELETE reports, notifications, users/me, uploads
- **Admin only:** stats/*, admin/*, POST/PUT/DELETE zones

### Protecciones
- CORS configurado con origins específicos
- CSRF deshabilitado (stateless JWT)
- Sesiones stateless (no cookies de sesión)
- Soft delete para usuarios (preserva historial)

---

## 12. Deployment

### Backend (Render)
- Dockerfile o buildpack Java 21
- Env vars configuradas en Render dashboard
- Health check: `/health` o `/actuator/health`
- BD: Supabase PostgreSQL (connection string en `application-prod.properties`)

### Frontend (Vercel/Netlify)
- Build: `npm run build` (Vite)
- Output: `dist/`
- Env var: `VITE_BACKEND_URL`

### Base de Datos
- Dev: H2 in-memory (si no hay profile prod) o PostgreSQL local
- Prod: Supabase PostgreSQL
- JPA `ddl-auto`: probablemente `update` (revisar application-prod.properties)

---

## 13. Problemas Conocidos y Decisiones Técnicas

### Resueltos ✅
| Problema | Solución | Commit |
|---|---|---|
| Race condition "Reporte no encontrado" | `TransactionSynchronization.afterCommit()` | `24f376b` |
| OpenRouter 429 rate limit | Retry + backoff exponencial + pipeline secuencial | `2f2d9ed` |
| GPS infinite loading en desktop | `enableHighAccuracy: false` + timeout 10s + hook | `2f2d9ed` |
| Gemma/Hermes bloqueados (429 global) | Switch a NVIDIA Nemotron + OpenAI GPT-OSS | `00a0501` |
| Reportes falsos (bromas, leyendas) | Heurística de filtrado + prompt strict | `c64e31e` |
| Fotos no se guardaban en el reporte | Conectar `photoUrl` del upload con submit | `e8451f7` |
| Dirección vacía si solo hay GPS | Reverse geocoding automático en backend | `431ff92` |

### Pendientes / Conocidos ⚠️
| Problema | Detalle |
|---|---|
| OSINT sin deduplicación | Ejecutar scan 2 veces crea reportes duplicados |
| OSINT clasificación síncrona | Usa `classifyReport()` en vez de `classifyAsync()` |
| OSINT sin geocodificación real | Location siempre es "Pasto" string |
| OSINT sin scheduler | Solo se ejecuta manualmente vía endpoint |
| OpenRouter tier gratuito | Sujeto a congestión global. $1 de crédito eliminaría el problema |
| Rate limiting por usuario | No implementado (un usuario podría spamear reportes) |
| Tests mínimos | Solo 1 test placeholder de OSINT |
| Nombres legacy en código | `GEMMA_MODEL` y `HERMES_MODEL` ya no son Gemma/Hermes |

---

## 14. Historial de Commits Recientes

```
607e53b feat: exclude REJECTED from public reports API
431ff92 fix: make address optional, backend auto-generates via reverse geocoding
81ddd05 feat: add incidentDate field, datetime picker in form
e8451f7 refactor: complete prompt rewrite with strict scoring
076b2ef fix: improve AI prompt penalizations
a845c4c fix: add @PostConstruct API key validation
c71ea28 fix(ai): update openrouter models to gemma-3-4b-it
00a0501 feat(ai): switch to NVIDIA Nemotron + OpenAI GPT-OSS
2f2d9ed refactor(backend): SOLID extraction - OpenRouterClient, ReportDecisionEngine, sequential pipeline
24f376b fix(backend): fix IA race condition, add retry backoff
67f791d feat(ia): Dual AI Ensemble with 3-rule consensus engine
93fb914 feat(ia): Add trustLevel penalty/bonus with rejection notifications
9518daf merge: RecuperarContraseña + fix email enumeration vulnerability
```

---

## 15. Sprint Plan (23 Abril → 6 Mayo 2026)

### Completado ✅
- Auth completo (login, register, forgot/reset password)
- Reportes CRUD + mapa interactivo con Leaflet
- IA Classification con pipeline secuencial SOLID
- WebSocket tiempo real (nuevo/actualizado/eliminado)
- Dashboard admin con stats, heatmap, timeline
- Admin panel (gestión usuarios, ban/unban, roles, moderación)
- Notificaciones persistentes
- File upload (fotos)
- Perfil de usuario editable
- Modal detalle de reporte con mini-mapa
- Mobile responsive con bottom sheet swipeable

### Pendiente
- [ ] OSINT: deduplicación, async, geocoding, scheduler
- [ ] Tests unitarios para servicios críticos
- [ ] Rate limiting por usuario
- [ ] Vista frontend OSINT (admin only)
- [ ] Vista frontend Preferencias de Alertas
- [ ] CI/CD pipeline
- [ ] PWA (Progressive Web App)

---

## 16. Convenciones del Código

### Backend (Java)
- **Paquetes:** controller → dto → service → repository → model
- **Naming:** PascalCase para clases, camelCase para métodos/variables
- **DTOs:** Siempre separar Request (CreateDTO) de Response (ResponseDTO)
- **Lombok:** `@Data`, `@Builder`, `@RequiredArgsConstructor` en todas las clases
- **Logs:** Usar `@Slf4j` con prefijos descriptivos: `[IA-Gemma]`, `[Pipeline]`, `[Consenso]`
- **Async:** `@Async` para operaciones pesadas (IA classification)
- **Transacciones:** `@Transactional` en service layer, NO en controllers

### Frontend (React)
- **Componentes:** Funcionales con hooks, NO clases
- **Estado:** Context API para auth/theme, props para estado local
- **Hooks custom:** Extraer lógica reutilizable a `src/hooks/`
- **API:** Todas las llamadas centralizadas en `src/services/api.js`
- **Estilos:** CSS variables para theming, NO inline styles (excepto dinámicos)
- **Iconos:** Lucide React exclusivamente

---

