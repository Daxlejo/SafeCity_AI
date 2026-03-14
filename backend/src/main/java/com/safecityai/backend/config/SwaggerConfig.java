package com.safecityai.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /**
     * Información general de la API que aparece en la
     * página principal de Swagger UI.
     */
    @Bean
    public OpenAPI safeCityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafeCity AI API")
                        .description("API REST para la plataforma de seguridad ciudadana inteligente SafeCity AI. "
                                + "Permite gestionar reportes, alertas en tiempo real, zonas de riesgo y análisis predictivo.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SafeCity AI Team")
                                .email("contacto@safecityai.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                );
    }

    /**
     * Agrupa todos los endpoints bajo /api/v1/**
     * para que aparezcan organizados en Swagger UI.
     */
    @Bean
    public GroupedOpenApi apiV1Group() {
        return GroupedOpenApi.builder()
                .group("v1")
                .displayName("SafeCity API v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
