package com.monitoring.poc;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers Postgres + full Spring context for integration tests.
 * The static container/DynamicPropertySource live on this base class so every
 * subclass reuses the same running container and cached Spring context
 * instead of paying startup cost per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("monitoring_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // No SMTP server is reachable in the test environment - EmailService
        // no-ops instead of every async send attempting a real socket connect.
        registry.add("app.mail.enabled", () -> "false");
        // Production default flipped to off while the SMTP relay is down;
        // integration tests still need the real 2FA flow exercised.
        registry.add("app.security.2fa-enabled", () -> "true");
    }

    @LocalServerPort
    protected int port;

    protected final TestRestTemplate restTemplate = new TestRestTemplate();

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
