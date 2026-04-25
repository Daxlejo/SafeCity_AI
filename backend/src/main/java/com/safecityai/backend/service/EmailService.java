package com.safecityai.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("SafeCity AI - Recuperación de contraseña");
            message.setText("Hola,\n\n" +
                    "Has solicitado restablecer tu contraseña.\n" +
                    "Usa el siguiente token para crear una nueva contraseña:\n\n" +
                    token + "\n\n" +
                    "Este token expirará en 30 minutos.\n" +
                    "Si no solicitaste este cambio, ignora este correo.\n");

            mailSender.send(message);
            log.info("Correo de recuperación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar correo de recuperación a {}: {}", to, e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo de recuperación", e);
        }
    }
}
