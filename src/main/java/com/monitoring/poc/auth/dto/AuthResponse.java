package com.monitoring.poc.auth.dto;

public class AuthResponse {

    private boolean requires2fa;
    private String tempToken;
    private String token;
    private String username;
    private String role;

    public AuthResponse() {
    }

    private AuthResponse(boolean requires2fa, String tempToken, String token, String username, String role) {
        this.requires2fa = requires2fa;
        this.tempToken = tempToken;
        this.token = token;
        this.username = username;
        this.role = role;
    }

    public static AuthResponse challenge(String tempToken) {
        return new AuthResponse(true, tempToken, null, null, null);
    }

    public static AuthResponse success(String token, String username, String role) {
        return new AuthResponse(false, null, token, username, role);
    }

    public boolean isRequires2fa() {
        return requires2fa;
    }

    public void setRequires2fa(boolean requires2fa) {
        this.requires2fa = requires2fa;
    }

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
