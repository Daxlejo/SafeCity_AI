package com.safecityai.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Servicio de correo electrónico usando Brevo (Sendinblue) API HTTP.
 * Se usa Brevo en lugar de SMTP/Resend porque:
 *  - Render Free Tier bloquea puertos SMTP (25, 465, 587).
 *  - Resend exige dominio propio verificado para enviar a terceros.
 *  - Brevo permite enviar a cualquier destinatario con solo un sender email verificado.
 * Plan gratuito: 300 emails/día, sin límite mensual de contactos.
 *
 * Docs: https://developers.brevo.com/reference/sendtransacemail
 */
@Slf4j
@Service
public class EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${app.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.brevo.sender-email:noreply@safecity.ai}")
    private String senderEmail;

    @Value("${app.brevo.sender-name:SafeCity AI}")
    private String senderName;

    @Value("${app.frontend.url:https://safe-cityai.vercel.app}")
    private String frontendUrl;

    private final RestTemplate restTemplate;

    public EmailService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Envía el correo de recuperación de contraseña al usuario.
     * Si la API key no está configurada, loguea un warning y no lanza excepción
     * para no bloquear el flujo (el token ya fue guardado en BD).
     */
    public void sendPasswordResetEmail(String to, String token) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("[Email] BREVO_API_KEY no configurada — correo NO enviado a {}. "
                    + "Configura la variable BREVO_API_KEY en las variables de entorno.", to);
            return;
        }

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        // trim() previene errores silenciosos por espacios en blanco al copiar el API key en Render
        String cleanApiKey = brevoApiKey.trim();

        log.debug("[Email] Intentando enviar a {} — sender: {} — key length: {}", to, senderEmail, cleanApiKey.length());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", cleanApiKey);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", "SafeCity AI - Recuperación de contraseña",
                    "htmlContent", buildPasswordResetHtml(resetLink)
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[Email] Correo de recuperación enviado exitosamente a {}", to);
            } else {
                log.error("[Email] Brevo retornó status {} para {}: {}", response.getStatusCode(), to, response.getBody());
            }

        } catch (Exception e) {
            log.error("[Email] Error al enviar correo de recuperación a {}: {}", to, e.getMessage());
            // No relanzar excepción — el token ya está guardado en BD,
            // el usuario puede solicitar otro correo después.
        }
    }

    /**
     * Construye el HTML del correo de recuperación de contraseña.
     */
    private String buildPasswordResetHtml(String resetLink) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Recuperación de contraseña</title>
                </head>
                <body style="margin:0;padding:0;background:#0f172a;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="520" cellpadding="0" cellspacing="0"
                               style="background:#1e293b;border-radius:16px;overflow:hidden;border:1px solid #334155;">
                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#3b82f6,#6366f1);padding:32px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">
                                🔒 SafeCity AI
                              </h1>
                              <p style="margin:8px 0 0;color:#bfdbfe;font-size:14px;">
                                Plataforma de Seguridad Ciudadana
                              </p>
                            </td>
                          </tr>
                          <!-- Body -->
                          <tr>
                            <td style="padding:32px;">
                              <h2 style="margin:0 0 12px;color:#f1f5f9;font-size:20px;font-weight:600;">
                                Recuperación de contraseña
                              </h2>
                              <p style="margin:0 0 24px;color:#94a3b8;font-size:15px;line-height:1.6;">
                                Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en SafeCity AI.
                                Haz clic en el botón de abajo para crear una nueva contraseña.
                              </p>
                              <div style="text-align:center;margin:28px 0;">
                                <a href="%s"
                                   style="display:inline-block;background:linear-gradient(135deg,#3b82f6,#6366f1);
                                          color:#ffffff;text-decoration:none;padding:14px 32px;
                                          border-radius:10px;font-size:15px;font-weight:600;
                                          letter-spacing:0.3px;">
                                  Restablecer contraseña
                                </a>
                              </div>
                              <p style="margin:24px 0 0;color:#64748b;font-size:13px;line-height:1.6;">
                                Este enlace expirará en <strong style="color:#94a3b8;">30 minutos</strong>.
                                Si no solicitaste este cambio, puedes ignorar este correo — tu cuenta está segura.
                              </p>
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="padding:20px 32px;border-top:1px solid #334155;text-align:center;">
                              <p style="margin:0;color:#475569;font-size:12px;">
                                © 2026 SafeCity AI · Pasto, Nariño, Colombia
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(resetLink);
    }
}
