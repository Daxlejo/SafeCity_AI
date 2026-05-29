package com.safecityai.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafeCityBackendApplication {

	public static void main(String[] args) {
		// Forzar a Java a usar IPv4 para evitar timeouts en conexiones SMTP (especialmente en Render)
		System.setProperty("java.net.preferIPv4Stack", "true");
		// Forzar zona horaria global para todos los LocalDateTime.now() (Solución Agente 1)
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Bogota"));
		SpringApplication.run(SafeCityBackendApplication.class, args);
	}

}
