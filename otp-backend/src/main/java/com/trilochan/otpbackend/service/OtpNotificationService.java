package com.trilochan.otpbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends OTP via Email only.
 *
 * <p>Flow: UI sends {@code ?email=user@example.com} (or legacy
 * {@code ?destination=}) to {@code /api/otp/generate}.
 *
 * <p>Modes ({@code app.otp.delivery-mode}): {@code email} (default) or
 * {@code log}. If SMTP credentials are missing, it falls back to log-mode
 * so local testing still works.
 */
@Service
public class OtpNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OtpNotificationService.class);

    @Value("${app.otp.delivery-mode:email}")
    private String deliveryMode;

    @Value("${app.otp.email-from:no-reply@example.com}")
    private String emailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    /** Result of a delivery attempt - tells UI whether Email really went out. */
    public record DeliveryResult(boolean delivered, String channel, String detail) {}

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public OtpNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public static boolean isEmail(String destination) {
        return destination != null && destination.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public DeliveryResult sendOtp(String email, String otp, int expiresInSeconds) {
        String target = (email == null || email.isBlank()) ? null : email.trim();
        String mode = deliveryMode == null ? "email" : deliveryMode.toLowerCase();

        boolean sent = false;
        String channel = "email";
        String detail;
        if (target == null) {
            channel = "log";
            detail = "No email given. OTP in backend console only.";
            log.info("[OTP][LOG-MODE] No email given. OTP={} (valid {}s).", otp, expiresInSeconds);
        } else if (!isEmail(target)) {
            channel = "log";
            detail = "Invalid email '" + target + "'. Use a valid email address.";
            log.warn("[OTP][EMAIL-SKIP] Invalid email '{}'. OTP logged only.", target);
        } else if (mode.equals("log")) {
            channel = "log";
            detail = "Delivery-mode=log. OTP in backend console only.";
            log.info("[OTP][LOG-MODE] To={} OTP={} (valid {}s).", target, otp, expiresInSeconds);
        } else {
            sent = sendEmail(target, otp, expiresInSeconds);
            detail = sent ? "Email OTP sent to " + target + "."
                    : "EMAIL NOT SENT - set MAIL_USERNAME + MAIL_PASSWORD (Gmail App Password), restart backend. OTP in console.";
        }

        if (!sent) {
            log.info("[OTP][FALLBACK-LOG] To={} OTP={} (valid {}s).", target == null ? "<no-email>" : target, otp, expiresInSeconds);
        }
        // Always print so local dev without log viewer still works.
        System.out.println("[OTP] To=" + (target == null ? "<no-email>" : target) + " OTP=" + otp
                + " (valid " + expiresInSeconds + "s)" + (sent ? " [SENT via email]" : " [LOG-ONLY] " + detail));
        return new DeliveryResult(sent, channel, detail);
    }

    public boolean isMailConfigured() {
        return mailHost != null && !mailHost.isBlank()
                && mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    private boolean sendEmail(String to, String otp, int expiresInSeconds) {
        if (!isMailConfigured()) {
            log.warn("[OTP][EMAIL-SKIP] SMTP not configured (need MAIL_USERNAME + MAIL_PASSWORD env vars). Cannot email {}. OTP logged only.", to);
            return false;
        }
        try {
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null) {
                log.warn("[OTP][EMAIL-SKIP] No JavaMailSender bean. OTP logged only.");
                return false;
            }
            // Gmail requires From == authenticated user. Prefer MAIL_USERNAME when set.
            String from = (mailUsername != null && !mailUsername.isBlank()) ? mailUsername.trim() : emailFrom;

            String textBody = "Your Email verification OTP is " + otp
                    + ". It is valid for " + expiresInSeconds + " seconds. Do not share it.";
            String htmlBody = "<div style='font-family:Arial,sans-serif;max-width:480px;margin:auto;border:1px solid #ddd;border-radius:8px;padding:24px'>"
                    + "<h2 style='margin-top:0'>Email Verification</h2>"
                    + "<p>Your one-time password is:</p>"
                    + "<p style='font-size:32px;font-weight:bold;letter-spacing:8px;margin:16px 0'>" + otp + "</p>"
                    + "<p>It is valid for <b>" + expiresInSeconds + " seconds</b>. Do not share it with anyone.</p>"
                    + "<p style='color:#888;font-size:12px'>If you did not request this, ignore this email.</p>"
                    + "</div>";

            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Your Email Verification OTP");
            helper.setText(textBody, htmlBody);
            sender.send(mime);
            log.info("[OTP][EMAIL-SENT] To={} From={}", to, from);
            return true;
        } catch (Exception ex) {
            log.error("[OTP][EMAIL-FAIL] To={} error={}", to, ex.getMessage());
            return false;
        }
    }
}
