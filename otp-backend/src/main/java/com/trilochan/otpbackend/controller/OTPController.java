package com.trilochan.otpbackend.controller;
import com.trilochan.otpbackend.dto.OTPResponse;
import com.trilochan.otpbackend.service.OTPService;
import com.trilochan.otpbackend.service.OtpNotificationService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class OTPController {

    private final OTPService otpService;
    private final OtpNotificationService notificationService;

    public OTPController(OTPService otpService, OtpNotificationService notificationService) {
        this.otpService = otpService;
        this.notificationService = notificationService;
    }

    @GetMapping("/api/otp/test")
    public String testOTP() {

        return "OTP Backend (Email verification) is working!";
    }

    /**
     * Diagnostic endpoint - open in browser to see WHY OTP email is not delivered.
     * Shows delivery-mode + whether SMTP credentials are present (never exposes secrets).
     */
    @GetMapping("/api/otp/delivery-status")
    public Map<String, Object> deliveryStatus() {
        return Map.of(
                "deliveryMode", notificationService.getDeliveryMode(),
                "mailConfigured", notificationService.isMailConfigured(),
                "hint", "For real Email OTP set MAIL_USERNAME / MAIL_PASSWORD env vars + restart. Without them, OTP prints in backend console (test mode)."
        );
    }

    @GetMapping("/api/otp/generate")
    public OTPResponse generateOTP(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "destination", required = false) String destination) {

        String resolved = resolveEmail(email, destination);
        validateEmail(resolved);
        return otpService.generateOTP(resolved);
    }

    @GetMapping("/api/otp/verify")
    public String verifyOTP(@RequestParam(value = "otp", required = false) String otp) {

        if (otp == null || !otp.matches("\\d{6}")) {
            return "Invalid OTP format! Enter exactly 6 digits.";
        }

        if (otpService.isExpired()) {
            return "Invalid or Expired OTP!";
        }

        if (otpService.isMaxAttemptsReached()) {
            return "Maximum attempts reached! Please resend OTP.";
        }

        if (otpService.isOTPValid(otp)) {

            return "OTP Verified Successfully!";
        }

        if (otpService.isMaxAttemptsReached()) {
            return "Maximum attempts reached! Please resend OTP.";
        }

        if (otpService.isExpired()) {
            return "Invalid or Expired OTP!";
        }

        return "Invalid OTP! Attempts remaining: " + otpService.getAttemptsRemaining();
    }
    @GetMapping("/api/otp/resend")
    public OTPResponse resendOTP(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "destination", required = false) String destination) {

        String resolved = resolveEmail(email, destination);
        validateEmail(resolved);
        return otpService.resendOTP(resolved);
    }

    /** Accept new `email` param, fall back to legacy `destination` param. */
    private String resolveEmail(String email, String destination) {
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        if (destination != null && !destination.isBlank()) {
            return destination.trim();
        }
        return null;
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return; // allowed in log/dev mode
        }
        if (!OtpNotificationService.isEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid email. Use a valid email address (e.g. user@example.com).");
        }
    }
}
