package com.safecityai.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SafeCityBackendApplication {

	public static void main(String[] args) {
		// Forzar a Java a usar IPv4 para evitar timeouts en conexiones SMTP (especialmente en Render)
		System.setProperty("java.net.preferIPv4Stack", "true");
		SpringApplication.run(SafeCityBackendApplication.class, args);
	}

}
