package com.safecityai.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String resetLink = frontendUrl + "/reset-password?token=" + token;

            message.setTo(to);
            message.setSubject("SafeCity AI - Recuperación de contraseña");
            message.setText("Hola,\n\n" +
                    "Has solicitado restablecer tu contraseña.\n" +
                    "Haz clic en el siguiente enlace para crear una nueva contraseña:\n\n" +
                    resetLink + "\n\n" +
                    "Este enlace expirará en 30 minutos.\n" +
                    "Si no solicitaste este cambio, ignora este correo.\n");

            mailSender.send(message);
            log.info("Correo de recuperación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar correo de recuperación a {}: {}", to, e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo de recuperación", e);
        }
    }
}
