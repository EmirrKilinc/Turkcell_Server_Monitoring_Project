package com.monitoring.poc.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class TwoFactorVerifyRequest {

    @NotBlank
    private String tempToken;

    @NotBlank
    private String otpCode;

    public TwoFactorVerifyRequest() {
    }

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
