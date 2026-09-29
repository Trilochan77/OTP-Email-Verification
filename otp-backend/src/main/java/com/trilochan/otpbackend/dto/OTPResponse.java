package com.trilochan.otpbackend.dto;

public class OTPResponse {

    private String message;
    private int expiresIn;
    private boolean delivered;
    private String channel;
    private String detail;
    private String testOtp;

    public OTPResponse(String message, int expiresIn) {
        this(message, expiresIn, false, "email", "LOG-ONLY: configure SMTP mail, OTP in backend console.", null);
    }

    public OTPResponse(String message, int expiresIn, boolean delivered, String channel, String detail) {
        this(message, expiresIn, delivered, channel, detail, null);
    }

    public OTPResponse(String message, int expiresIn, boolean delivered, String channel, String detail, String testOtp) {
        this.message = message;
        this.expiresIn = expiresIn;
        this.delivered = delivered;
        this.channel = channel;
        this.detail = detail;
        this.testOtp = testOtp;
    }

    public String getMessage() {
        return message;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public String getChannel() {
        return channel;
    }

    public String getDetail() {
        return detail;
    }

    /** Only set when test-mode is ON and SMS/Email was NOT really sent. Null in production. */
    public String getTestOtp() {
        return testOtp;
    }
}