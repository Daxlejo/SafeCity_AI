# Reglas Globales - SafeCity AI

## 1. Identidad y Equipo
- **Proyecto:** Plataforma de seguridad ciudadana para Pasto, Colombia.
- **Institución:** Universidad Cooperativa de Colombia.
- **Equipo:** David Alejandro (Lead), Cristian Yela (Backend), Edgar (Frontend).

## 2. Protocolo de Integración de Frontend (CRÍTICO)
- **Entorno de Lectura:** La carpeta `frontend_review/` es estrictamente de **SOLO LECTURA**. El agente jamás debe escribir ni modificar archivos en esta ruta.
- **Proceso de Migración:** Para integrar vistas de Edgar a la rama principal:
    1. Leer el código fuente desde `frontend_review/`.
    2. Adaptar el código a los estándares de `frontend_src/`.
    3. Reemplazar peticiones HTTP directas por llamadas al servicio centralizado en `src/services/api.js`.
    4. Conectar hooks globales (`AuthContext`, `ThemeContext`, `useGeolocation`).
    5. Asegurar el uso de **Vanilla CSS** con variables globales (prohibido Tailwind o inline styles masivos).
    6. Registrar la nueva ruta en `App.jsx` y verificar la responsividad (Bottom Sheet en mobile).

## 3. Estándares de Código
- **Paradigma y Calidad:** Aplicar siempre principios de **Programación Orientada a Objetos (POO)** en el backend y buenas prácticas de Clean Code (SRP, DRY).
- **Idiomas:**
    - **Inglés:** TODO el código fuente (variables, métodos, clases, endpoints, tablas de BD).
    - **Español:** Exclusivamente para la Interfaz de Usuario (UI), mensajes de error visibles para el usuario y commits.
    - **Comentarios (Español):** Solo se permiten cuando sean **estrictamente necesarios** para explicar decisiones complejas o lógica de negocio no evidente. Se prohíben comentarios redundantes (ej. no escribir `// Obtiene el usuario` encima de un método `getUser()`).
- **Naming:**
    - Backend: PascalCase para clases, camelCase para métodos/atributos.
    - Frontend: PascalCase para componentes (`.jsx`), camelCase para hooks y funciones.
