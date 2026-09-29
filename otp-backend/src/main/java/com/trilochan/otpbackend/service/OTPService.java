package com.trilochan.otpbackend.service;

import com.trilochan.otpbackend.dto.OTPResponse;
import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OTPService {

    public static final int OTP_VALIDITY_SECONDS = 60;
    public static final int MAX_ATTEMPTS = 3;

    /** Dev-only: expose OTP in API response when real SMS/Email NOT sent. Disable in production. */
    @Value("${app.otp.expose-for-testing:true}")
    private boolean exposeForTesting;

    private final SecureRandom random = new SecureRandom();
    private final OtpNotificationService notificationService;

    private String generatedOTP;
    private LocalDateTime expiryTime;
    private int attempts = 0;

    public OTPService(OtpNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public synchronized OTPResponse generateOTP() {
        return generateOTP(null);
    }

    public synchronized OTPResponse generateOTP(String destination) {
        int otp = 100000 + random.nextInt(900000);

        generatedOTP = String.valueOf(otp);
        attempts = 0;
        expiryTime = LocalDateTime.now().plusSeconds(OTP_VALIDITY_SECONDS);

        // Real SMS/Email send - returns whether it actually went out.
        OtpNotificationService.DeliveryResult result =
                notificationService.sendOtp(destination, generatedOTP, OTP_VALIDITY_SECONDS);

        return new OTPResponse(
                result.delivered() ? "OTP sent successfully via email"
                        : "OTP generated (TEST MODE - no login needed, use testOtp)",
                OTP_VALIDITY_SECONDS,
                result.delivered(),
                result.channel(),
                result.detail(),
                (!result.delivered() && exposeForTesting) ? generatedOTP : null
        );
    }

    public synchronized boolean isOTPValid(String enteredOTP) {
        if (generatedOTP == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(expiryTime)) {
            return false;
        }

        if (attempts >= MAX_ATTEMPTS) {
            return false;
        }

        if (enteredOTP != null && enteredOTP.equals(generatedOTP)) {
            // Consume OTP so it cannot be reused.
            generatedOTP = null;
            return true;
        }

        attempts++;
        return false;
    }

    public synchronized OTPResponse resendOTP() {
        return resendOTP(null);
    }

    public synchronized OTPResponse resendOTP(String destination) {
        // Old OTP is implicitly invalidated by overwrite.
        System.out.println("Old OTP invalidated.");
        return generateOTP(destination);
    }

    public synchronized int getAttemptsRemaining() {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }

    public synchronized boolean isExpired() {
        return expiryTime != null && LocalDateTime.now().isAfter(expiryTime);
    }

    public synchronized boolean isMaxAttemptsReached() {
        return attempts >= MAX_ATTEMPTS;
    }
}