package com.monitoring.poc.security;

import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-value-that-is-long-enough-for-hs256", 60_000L);

    private User sampleUser() {
        User user = new User("alice", "alice@example.com", "hash", Role.OPERATOR, UserStatus.APPROVED);
        user.setId(1L);
        return user;
    }

    @Test
    void generatesTokenWithExpectedClaims() {
        String token = jwtService.generateToken(sampleUser());

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractRole(token)).isEqualTo("OPERATOR");
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService("test-secret-value-that-is-long-enough-for-hs256", 10L);
        String token = shortLived.generateToken(sampleUser());

        Thread.sleep(50);

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.isValid("not-a-real-token")).isFalse();
    }
}
