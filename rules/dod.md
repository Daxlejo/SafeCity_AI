# Criterios de Validación (DoD)

## 1. Calidad Técnica
- **Backend:** No se permite lógica de negocio en los Controladores; todo debe residir en la capa de `Service`.
- **Frontend:** Todo componente nuevo debe ser responsive y soportar Dark/Light mode mediante el `ThemeContext`.
- **Clean Code:** Uso obligatorio de Lombok para reducir boilerplate en Java.

## 2. Rendimiento (RNF)
- **Latencia:** Las consultas de estadísticas y listados de reportes deben responder en menos de 3 segundos.
- **Concurrencia:** El sistema debe manejar múltiples análisis de IA simultáneos mediante el pool de hilos configurado en `AsyncConfig`.

## 3. Pruebas
- Cada nueva funcionalidad de servicio debe incluir al menos un test unitario que valide el flujo principal.
