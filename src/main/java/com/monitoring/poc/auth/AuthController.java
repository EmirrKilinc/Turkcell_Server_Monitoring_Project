package com.monitoring.poc.auth;

import com.monitoring.poc.auth.dto.AuthResponse;
import com.monitoring.poc.auth.dto.LoginRequest;
import com.monitoring.poc.auth.dto.RegisterRequest;
import com.monitoring.poc.auth.dto.ResendOtpRequest;
import com.monitoring.poc.auth.dto.TwoFactorVerifyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verify2fa(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/resend-2fa")
    public ResponseEntity<AuthResponse> resend2fa(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request));
    }

    @GetMapping("/2fa-status")
    public ResponseEntity<Map<String, Boolean>> twoFactorStatus() {
        return ResponseEntity.ok(Map.of("enabled", authService.isTwoFactorEnabled()));
    }
}
