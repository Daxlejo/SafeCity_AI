package com.safecityai.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ═══════════════════════════════════════════════════════════════
 * CONFIGURACIÓN @Async — Ejecución en background
 * ═══════════════════════════════════════════════════════════════
 *
 * ¿Qué es @Async?
 * ─────────────────
 * Normalmente, cuando llamas un método en Java, el hilo actual
 * ESPERA a que ese método termine antes de continuar.
 * Con @Async, Spring ejecuta el método en un HILO SEPARADO,
 * y el hilo original sigue ejecutándose sin esperar.
 *
 * ¿Por qué un ThreadPool?
 * ─────────────────────────
 * Crear un hilo nuevo por cada reporte sería costoso.
 * Un ThreadPool reutiliza hilos existentes (como una piscina
 * de trabajadores). Configuramos:
 *   - corePoolSize=2  → 2 hilos siempre listos
 *   - maxPoolSize=5   → máximo 5 hilos si hay mucha carga
 *   - queueCapacity=25 → cola de espera de 25 tareas
 *
 * Esto es el patrón PRODUCER-CONSUMER:
 *   ReportService (productor) → cola → ThreadPool (consumidor)
 */
@Configuration
@EnableAsync  // ← Activa el soporte para @Async en toda la app
public class AsyncConfig {

    @Bean(name = "iaExecutor")
    public Executor iaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("IA-Classify-"); // Para identificar en logs
        executor.initialize();
        return executor;
    }
}
