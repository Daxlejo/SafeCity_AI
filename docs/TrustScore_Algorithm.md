# Algoritmo de Trust Score - SafeCity AI
*Documentación para Pruebas (QA)*

El Motor de IA de SafeCity evalúa la veracidad de los reportes calculando un **Trust Score (0-100)** y asignando un nivel de confianza (`TrustLevel`).

Actualmente, el sistema funciona con **dos fases**:
1. **Fase Principal (Gemini AI)**: Análisis semántico del reporte.
2. **Fase de Fallback (Heurística)**: Sistema de reglas fijas en caso de que la IA no responda correctamente.

---

## 2. Niveles de Confianza (Trust Level)
Todo el sistema traduce el puntaje numérico a un `TrustLevel`:

- `VERIFIED` (> 80 puntos)
- `HIGH` (60 - 80 puntos)
- `MODERATE` (40 - 60 puntos)
- `LOW` (20 - 40 puntos)
- `UNTRUSTED` (< 20 puntos)

---

## 3. Fase Principal: Análisis NLP (Gemini AI)

El modelo de lenguaje recibe el reporte completo (descripción, fuente, metadatos) y evalúa la confianza usando las siguientes directrices y reglas de puntuación insertadas en el _Prompt_:

- **Base por defecto:** 30 puntos
- **Rechazo automático (0 puntos):** Si el reporte describe noticias políticas, operativos preventivos generales o situaciones pasadas, es descartado.
- **Detalle de descripción:** +20 a +30 puntos si es detallada y coherente.
- **Vaguedad:** -10 a -30 puntos si el texto carece de claridad.
- **Validación Geoespacial:** +15 puntos si vienen coordenadas (lat/lng).
- **Evidencia Visual:** +20 puntos si contiene `photoUrl`.
- **Fuente:** +10 puntos si es directo de los ciudadanos.

### 🧠 Ejemplo Numérico 1 (Analizado por Gemini)
**Reporte:** "Me acaban de robar el celular a mano armada dos sujetos en moto, cerca a la Plaza del Carnaval (Pasto). Tengo placa."
- **Datos adjuntos:** `latitude/longitude`, `photoUrl` = null.
**Cálculo aproximado esperado (por IA):**
- Base: 30
- Coordenadas (GPS): +15
- Fuente Ciudadana: +10
- Descripción coherente y descriptiva: +25
**🔥 Trust Score final esperado:** ~80 puntos (`HIGH` o `VERIFIED`)

---

## 4. Fase 2 de Respaldo: Sistema Heurístico (Fallback)

Si la llamada a la IA de Gemini falla, el servicio `IAClassificationService.java` implementa un algoritmo matemático sólido con un máximo (`Cap`) de **100**.

### Estructura de Ponderación Actual:
- **Base:** 30 puntos
- **Longitud de Descripción (`description`):**
  - > 100 caracteres: +25 puntos
  - > 50 caracteres: +15 puntos
  - > 20 caracteres: +5 puntos
- **Coordenadas de GPS enviadas (`latitude` & `longitude`):** +20 puntos
- **Dirección manual enviada (`address`):** +10 puntos
- **Evidencia fotográfica (`photoUrl`):** +25 puntos
- **Ponderación por Fuente (`source`):**
  - `CITIZEN_TEXT` / `CITIZEN_VOICE`: +15 puntos
  - `INSTITUTIONAL`: +10 puntos
  - `SOCIAL_MEDIA`: +5 puntos

### 📐 Ejemplo Numérico 2 (Calculado por Fallback)
**Reporte:** "Me robaron el celular en la Calle 18." (41 caracteres) enviado por texto `CITIZEN_TEXT`, incluye dirección escrita pero no GPS ni Foto.
**Cálculo Exacto:**
- Base = 30
- Longitud (41 chars) = +5 (es > 20 pero < 50)
- GPS = +0
- Dirección (tiene address) = +10
- Fuente (CITIZEN_TEXT) = +15
- Foto = +0
**🔥 Trust Score final:** `30 + 5 + 10 + 15 = 60 puntos` (`HIGH`)

### 📐 Ejemplo Numérico 3 (Máxima confianza por Fallback)
**Reporte:** Un ciudadano redacta una situación de más de 100 caracteres sobre un choque masivo de tránsito en la Avenida de los Estudiantes (Pasto), enviando GPS, Dirección textual, y también incluye Foto.
**Cálculo Exacto:**
- Base = 30
- Longitud (> 100) = +25
- GPS = +20
- Dirección = +10
- Fuente Institucional o Ciudadano = +15
- Foto = +25
**Total Ponderado:** `125 puntos`
**🔥 Capped Trust Score final:** `100 puntos` (`VERIFIED`)

---

## 5. Recomendación Técnica para Próximas Fases
Al revisar el motor actual, detectamos que **no** se ha implementado todavía el cruce de reportes cercanos ni la bonificación por `trustLevel` histórico de usuarios especificados en los requerimientos iniciales (+10 puntos por cercanía en <500m / <2h y +15 puntos si user trust > 70).
Se recomienda integrar las coordenadas con la base de datos PostgreSQL utilizando validaciones de cercanía (`Haversine` o `PostGIS`) en el Sprint respectivo.
