# ═══════════════════════════════════
# Multi-stage build para Spring Boot en Render
# ═══════════════════════════════════

# --- Etapa 1: BUILD ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiar Maven wrapper y pom.xml primero (cache de dependencias)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:resolve -B

# Copiar código fuente y compilar
COPY src ./src
RUN ./mvnw package -DskipTests -B

# --- Etapa 2: RUN ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Forzar IPv4 — Supabase puede rechazar conexiones IPv6
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses=true"

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Render asigna PORT dinámicamente via env var
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
