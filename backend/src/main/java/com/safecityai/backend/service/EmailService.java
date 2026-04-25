package com.safecityai.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Value("${spring.mail.username:}")
    private String mailFrom;

    // @Autowired(required = false) permite que la app arranque
    // aunque no haya SMTP configurado (MAIL_USERNAME / MAIL_PASSWORD vacíos).
    // En ese caso mailSender será null y el correo simplemente no se envía.
    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String token) {
        if (mailSender == null) {
            log.warn("JavaMailSender no configurado — correo de recuperación NO enviado a {}. "
                    + "Configura MAIL_USERNAME y MAIL_PASSWORD para habilitar emails.", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String resetLink = frontendUrl + "/reset-password?token=" + token;

            message.setFrom("SafeCity AI <" + mailFrom + ">");
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
            // No relanzar excepción — el token ya está guardado en BD,
            // el usuario puede solicitar otro correo después.
        }
    }
}
