package com.monitoring.poc;

import com.monitoring.poc.admin.dto.UserSummaryResponse;
import com.monitoring.poc.auth.dto.AuthResponse;
import com.monitoring.poc.auth.dto.LoginRequest;
import com.monitoring.poc.auth.dto.RegisterRequest;
import com.monitoring.poc.entity.UserOtpVerification;
import com.monitoring.poc.repository.UserOtpVerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String BOOTSTRAP_ADMIN_USERNAME = "admin";
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "ChangeMe123!";

    @Autowired
    private UserOtpVerificationRepository userOtpVerificationRepository;

    @Test
    void registerStaysLockedOutUntilAdminApprovesThenLoginSucceeds() {
        String username = "flowuser_" + System.nanoTime();

        RegisterRequest register = new RegisterRequest();
        register.setUsername(username);
        register.setEmail(username + "@example.com");
        register.setPassword("Password123!");

        ResponseEntity<Void> registerResponse = restTemplate.postForEntity(
                baseUrl("/api/auth/register"), register, Void.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword("Password123!");

        ResponseEntity<Map> pendingLoginAttempt = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, Map.class);
        assertThat(pendingLoginAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = loginAndGetToken(BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD);
        Long pendingUserId = findUserIdByUsername(adminToken, username);
        approveUser(adminToken, pendingUserId);

        ResponseEntity<AuthResponse> approvedLogin = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);
        assertThat(approvedLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approvedLogin.getBody().getToken()).isNotBlank();
        assertThat(approvedLogin.getBody().getRole()).isEqualTo("VIEWER");

        // Approved user's token now works against a protected endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(approvedLogin.getBody().getToken());
        ResponseEntity<Object[]> serversResponse = restTemplate.exchange(
                baseUrl("/api/servers"), HttpMethod.GET, new HttpEntity<>(headers), Object[].class);
        assertThat(serversResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordIsRejected() {
        LoginRequest login = new LoginRequest();
        login.setUsername(BOOTSTRAP_ADMIN_USERNAME);
        login.setPassword("definitely-wrong");

        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl("/api/auth/login"), login, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminLoginReturnsTwoFactorChallengeInsteadOfATokenDirectly() {
        LoginRequest login = new LoginRequest();
        login.setUsername(BOOTSTRAP_ADMIN_USERNAME);
        login.setPassword(BOOTSTRAP_ADMIN_PASSWORD);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isRequires2fa()).isTrue();
        assertThat(response.getBody().getTempToken()).isNotBlank();
        assertThat(response.getBody().getToken()).isNull();
    }

    @Test
    void wrongOtpCodeIsRejectedWithBadRequest() {
        LoginRequest login = new LoginRequest();
        login.setUsername(BOOTSTRAP_ADMIN_USERNAME);
        login.setPassword(BOOTSTRAP_ADMIN_PASSWORD);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);
        String tempToken = loginResponse.getBody().getTempToken();

        ResponseEntity<Map> verifyResponse = restTemplate.postForEntity(
                baseUrl("/api/auth/verify-2fa"), Map.of("tempToken", tempToken, "otpCode", "000000"), Map.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resendOtpIssuesAFreshWorkingCodeAndInvalidatesTheOldOne() {
        LoginRequest login = new LoginRequest();
        login.setUsername(BOOTSTRAP_ADMIN_USERNAME);
        login.setPassword(BOOTSTRAP_ADMIN_PASSWORD);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);
        String originalTempToken = loginResponse.getBody().getTempToken();

        ResponseEntity<AuthResponse> resendResponse = restTemplate.postForEntity(
                baseUrl("/api/auth/resend-2fa"), Map.of("tempToken", originalTempToken), AuthResponse.class);
        assertThat(resendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newTempToken = resendResponse.getBody().getTempToken();
        assertThat(newTempToken).isNotEqualTo(originalTempToken);

        // Old tempToken no longer resolves to a usable OTP row.
        ResponseEntity<Map> oldTokenAttempt = restTemplate.postForEntity(
                baseUrl("/api/auth/verify-2fa"), Map.of("tempToken", originalTempToken, "otpCode", "123456"), Map.class);
        assertThat(oldTokenAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String token = completeTwoFactorLogin(newTempToken);
        assertThat(token).isNotBlank();
    }

    private String loginAndGetToken(String username, String password) {
        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword(password);
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse body = response.getBody();
        if (body.isRequires2fa()) {
            return completeTwoFactorLogin(body.getTempToken());
        }
        return body.getToken();
    }

    private String completeTwoFactorLogin(String tempToken) {
        UserOtpVerification otp = userOtpVerificationRepository.findByTempTokenAndIsUsedFalse(tempToken)
                .orElseThrow();
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl("/api/auth/verify-2fa"), Map.of("tempToken", tempToken, "otpCode", otp.getOtpCode()),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getToken();
    }

    private Long findUserIdByUsername(String adminToken, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<UserSummaryResponse[]> response = restTemplate.exchange(
                baseUrl("/api/admin/users?status=PENDING"), HttpMethod.GET,
                new HttpEntity<>(headers), UserSummaryResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (UserSummaryResponse user : response.getBody()) {
            if (user.getUsername().equals(username)) {
                return user.getId();
            }
        }
        throw new IllegalStateException("Pending user not found: " + username);
    }

    private void approveUser(String adminToken, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("status", "APPROVED"), headers);

        ResponseEntity<UserSummaryResponse> response = restTemplate.exchange(
                baseUrl("/api/admin/users/" + userId + "/status"), HttpMethod.PATCH, entity, UserSummaryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
