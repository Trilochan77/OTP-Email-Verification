// ==========================================
// Email OTP Web UI - client for Spring Boot backend
// Backend: http://localhost:8080/api/otp/*
// Rules: 6-digit, 60s expiry, 3 attempts, Email only
// ==========================================

const BASE_URL = "http://localhost:8080/api/otp";

const otpInput = document.getElementById("otp");
const timer = document.getElementById("timer");
const verifyBtn = document.getElementById("verifyBtn");
const resendBtn = document.getElementById("resendBtn");
const sendBtn = document.getElementById("sendBtn");
const status = document.getElementById("status");
const step1 = document.getElementById("step1");
const step2 = document.getElementById("step2");
const timerFill = document.getElementById("timerFill");

// Email address used by backend Email sender.
const destinationInput = document.getElementById("destination");

let timeRemaining = 60;
let countdown;

function getEmail() {
    if (destinationInput && destinationInput.value.trim() !== "") {
        return destinationInput.value.trim();
    }
    return null;
}

function isValidEmail(email) {
    if (!email) return false;
    return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);
}

function buildUrl(path, params) {
    let url = BASE_URL + path;
    if (params) {
        const qs = new URLSearchParams(params).toString();
        if (qs) url += "?" + qs;
    }
    return url;
}

// ==========================================
// Generate OTP (Email only)
// ==========================================
async function generateOTP() {
    try {
        const email = getEmail();
        if (!email) {
            setStatus("Enter your email address first.");
            return;
        }
        if (!isValidEmail(email)) {
            setStatus("INVALID EMAIL - use a valid email like user@example.com.");
            return;
        }
        const params = { email: email };

        const response = await fetch(buildUrl("/generate", params));
        if (!response.ok) {
            const errText = await response.text();
            throw new Error(errText || ("HTTP " + response.status));
        }

        const data = await response.json();
        console.log("Backend Response:", data);

        const expiresIn = Number.isFinite(data.expiresIn) ? data.expiresIn : 60;
        startCountdown(expiresIn);
        if (data.delivered) {
            setStatus("ACTIVE - OTP sent via Email to " + email + ".");
            showTestOtp(null);
        } else {
            // TEST MODE: SMTP not configured - backend returns testOtp for copy-paste.
            setStatus("ACTIVE (TEST MODE) - no email sent, use test OTP below.");
            console.warn("OTP NOT emailed:", data.detail);
            showTestOtp(data.testOtp || null);
        }
    } catch (error) {
        console.error("Error generating OTP:", error);
        setStatus("SERVER ERROR - " + error.message);
        timer.textContent = "Could not send OTP.";
    }
}

// ==========================================
// Start Countdown (single entry point)
// ==========================================
function startCountdown(seconds) {
    clearInterval(countdown);

    timeRemaining = seconds;
    const total = seconds > 0 ? seconds : 60;

    timer.textContent = "Time remaining: " + timeRemaining + " seconds";
    if (timerFill) timerFill.style.width = "100%";
    verifyBtn.disabled = false;
    setStep(2);

    countdown = setInterval(() => {
        timeRemaining--;
        if (timeRemaining <= 0) {
            clearInterval(countdown);
            timeRemaining = 0;
            timer.textContent = "OTP has expired!";
            if (timerFill) timerFill.style.width = "0%";
            setStatus("EXPIRED");
            verifyBtn.disabled = true;
        } else {
            timer.textContent = "Time remaining: " + timeRemaining + " seconds";
            if (timerFill) timerFill.style.width = Math.round((timeRemaining / total) * 100) + "%";
        }
    }, 1000);
}

function setStep(n) {
    if (!step1 || !step2) return;
    if (n === 1) {
        step1.className = "active";
        step2.className = "";
    } else {
        step1.className = "done";
        step2.className = "active";
    }
}

function setStatus(text) {
    const label = text.startsWith("Status:") ? text : "Status: " + text;
    status.textContent = label;
    // color coding
    let cls = "status";
    if (/VERIFIED/.test(text)) cls += " verified";
    else if (/ACTIVE/.test(text)) cls += " active";
    else if (/SERVER ERROR|INVALID|MAX ATTEMPTS|does not|valid email/i.test(text)) cls += " error";
    else if (/EXPIRED/.test(text)) cls += " expired";
    else cls += " idle";
    status.className = cls;
    if (/VERIFIED/.test(text)) setStep(2);
}

// Test-mode helper: shows test OTP box + click-to-fill, no login needed.
function showTestOtp(otp) {
    let box = document.getElementById("testOtpBox");
    if (!otp) {
        if (box) box.remove();
        return;
    }
    if (!box) {
        box = document.createElement("p");
        box.id = "testOtpBox";
        box.title = "Click to autofill";
        status.after(box);
        box.addEventListener("click", () => {
            otpInput.value = box.dataset.otp || "";
            otpInput.focus();
        });
    }
    box.dataset.otp = otp;
    box.textContent = "TEST OTP (no login needed): " + otp + " — click to fill";
}

// ==========================================
// No auto-send on load: user must enter email + press Send OTP
// (saves email cost, matches real flow)
// ==========================================
timer.textContent = "Enter email and press Send OTP.";
verifyBtn.disabled = true;
setStatus("IDLE - waiting for email.");
setStep(1);

// OTP input: digits only
otpInput.addEventListener("input", () => {
    otpInput.value = otpInput.value.replace(/[^0-9]/g, "").slice(0, 6);
});

if (sendBtn) {
    sendBtn.addEventListener("click", generateOTP);
} else {
    generateOTP();
}

// ==========================================
// Verify OTP
// ==========================================
verifyBtn.addEventListener("click", async () => {
    const enteredOTP = otpInput.value.trim();

    if (!/^\d{6}$/.test(enteredOTP)) {
        setStatus("INVALID INPUT - enter exactly 6 digits.");
        otpInput.value = "";
        otpInput.focus();
        return;
    }

    if (timeRemaining <= 0) {
        setStatus("EXPIRED");
        otpInput.value = "";
        return;
    }

    try {
        const response = await fetch(buildUrl("/verify", { otp: enteredOTP }));
        const result = await response.text();
        console.log("Verify Response:", result);

        if (result === "OTP Verified Successfully!") {
            clearInterval(countdown);
            setStatus("VERIFIED");
            timer.textContent = "OTP verified successfully!";
            verifyBtn.disabled = true;
        } else if (result.includes("Maximum attempts")) {
            setStatus("MAX ATTEMPTS REACHED - press Resend.");
            verifyBtn.disabled = true;
            otpInput.value = "";
        } else if (result.includes("Expired")) {
            clearInterval(countdown);
            setStatus("EXPIRED");
            timer.textContent = "OTP has expired!";
            verifyBtn.disabled = true;
            otpInput.value = "";
        } else {
            // "Invalid OTP! Attempts remaining: X" or format error
            setStatus(result);
            otpInput.value = "";
            otpInput.focus();
        }
    } catch (error) {
        console.error("Error verifying OTP:", error);
        setStatus("SERVER ERROR");
    }
});

// ==========================================
// Resend OTP (uses /resend so old OTP is invalidated server-side)
// ==========================================
resendBtn.addEventListener("click", async () => {
    otpInput.value = "";
    try {
        const email = getEmail();
        if (!email || !isValidEmail(email)) {
            setStatus("Enter a valid email before resending.");
            return;
        }
        const params = { email: email };

        const response = await fetch(buildUrl("/resend", params));
        if (!response.ok) throw new Error("HTTP " + response.status);

        const data = await response.json();
        console.log("Resend Response:", data, "Old OTP invalidated.");

        const expiresIn = Number.isFinite(data.expiresIn) ? data.expiresIn : 60;
        startCountdown(expiresIn);
        if (data.delivered) {
            setStatus("ACTIVE - OTP resent via Email.");
            showTestOtp(null);
        } else {
            setStatus("ACTIVE (TEST MODE) - use test OTP below.");
            showTestOtp(data.testOtp || null);
        }
    } catch (error) {
        console.error("Error resending OTP:", error);
        setStatus("SERVER ERROR - could not resend.");
    }
});
