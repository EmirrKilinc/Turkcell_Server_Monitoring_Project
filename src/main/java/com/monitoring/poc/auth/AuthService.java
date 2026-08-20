package com.monitoring.poc.auth;

import com.monitoring.poc.auth.dto.AuthResponse;
import com.monitoring.poc.auth.dto.LoginRequest;
import com.monitoring.poc.auth.dto.RegisterRequest;
import com.monitoring.poc.auth.dto.ResendOtpRequest;
import com.monitoring.poc.auth.dto.TwoFactorVerifyRequest;
import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.entity.UserOtpVerification;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.UserOtpVerificationRepository;
import com.monitoring.poc.repository.UserRepository;
import com.monitoring.poc.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int OTP_EXPIRY_MINUTES = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserOtpVerificationRepository otpRepository;
    private final EmailService emailService;
    private final boolean twoFactorEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        UserOtpVerificationRepository otpRepository, EmailService emailService,
                        @Value("${app.security.2fa-enabled}") boolean twoFactorEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu kullanici adi zaten kayitli");
        }

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                Role.VIEWER,
                UserStatus.PENDING
        );
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Kullanici adi veya sifre hatali"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Kullanici adi veya sifre hatali");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hesabiniz henuz bir admin tarafindan onaylanmadi");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hesabiniz reddedildi");
        }

        // Email-based 2FA gates ADMIN/OPERATOR only - VIEWER keeps today's
        // single-step login since it's a low-privilege, self-registered role.
        // The whole gate can also be killed via app.security.2fa-enabled
        // (e.g. while the SMTP relay is unreachable) without touching code.
        if (!twoFactorEnabled || user.getRole() == Role.VIEWER) {
            String token = jwtService.generateToken(user);
            return AuthResponse.success(token, user.getUsername(), user.getRole().name());
        }
        return issueOtpChallenge(user);
    }

    public AuthResponse verifyOtp(TwoFactorVerifyRequest request) {
        UserOtpVerification otp = otpRepository.findByTempTokenAndIsUsedFalse(request.getTempToken())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Gecersiz veya suresi dolmus dogrulama"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kod suresi doldu, lutfen tekrar giris yapin");
        }
        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Kod hatali");
        }

        otp.setIsUsed(true);
        otpRepository.save(otp);

        User user = otp.getUser();
        String token = jwtService.generateToken(user);
        return AuthResponse.success(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse resendOtp(ResendOtpRequest request) {
        UserOtpVerification existing = otpRepository.findByTempTokenAndIsUsedFalse(request.getTempToken())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Gecersiz dogrulama oturumu"));

        existing.setIsUsed(true);
        otpRepository.save(existing);

        return issueOtpChallenge(existing.getUser());
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    private AuthResponse issueOtpChallenge(User user) {
        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));
        String tempToken = UUID.randomUUID().toString();

        UserOtpVerification otp = new UserOtpVerification(user, otpCode, tempToken,
                LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otp);

        emailService.sendOtpEmail(user.getEmail(), otpCode);
        return AuthResponse.challenge(tempToken);
    }
}
