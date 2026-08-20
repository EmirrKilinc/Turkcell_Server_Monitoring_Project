package com.monitoring.poc;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.security.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MetricIngestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private CryptoUtil cryptoUtil;

    private String seedServer(String hostname, String rawSecret, ServerStatus status) {
        Server server = new Server(hostname, "10.5.5.5", "monitoring_user",
                cryptoUtil.encrypt(rawSecret), status, null);
        serverRepository.save(server);
        return rawSecret;
    }

    private String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private HttpEntity<String> signedEntity(String hostname, String secret, String body, long timestamp) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Timestamp", String.valueOf(timestamp));
        headers.add("X-Signature", sign(secret, timestamp, body));
        headers.add("X-Server-Hostname", hostname);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void validSignatureIsAcceptedAndPersisted() throws Exception {
        String hostname = "ingest-host-" + System.nanoTime();
        String secret = seedServer(hostname, "raw-secret-value-1", ServerStatus.ACTIVE);

        String body = """
                {"hostname":"%s","cpuPercent":11.1,"ramPercent":22.2,"diskPercent":33.3}
                """.formatted(hostname).strip();
        long timestamp = Instant.now().getEpochSecond();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl("/api/metrics"), signedEntity(hostname, secret, body, timestamp), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        String hostname = "ingest-host-" + System.nanoTime();
        String secret = seedServer(hostname, "raw-secret-value-2", ServerStatus.ACTIVE);

        String signedBody = """
                {"hostname":"%s","cpuPercent":1.0,"ramPercent":1.0,"diskPercent":1.0}
                """.formatted(hostname).strip();
        long timestamp = Instant.now().getEpochSecond();
        HttpEntity<String> entity = signedEntity(hostname, secret, signedBody, timestamp);

        // Tamper with the body after signing, before sending
        String tamperedBody = """
                {"hostname":"%s","cpuPercent":99.9,"ramPercent":1.0,"diskPercent":1.0}
                """.formatted(hostname).strip();
        HttpEntity<String> tamperedEntity = new HttpEntity<>(tamperedBody, entity.getHeaders());

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl("/api/metrics"), tamperedEntity, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        String hostname = "ingest-host-" + System.nanoTime();
        String secret = seedServer(hostname, "raw-secret-value-3", ServerStatus.ACTIVE);

        String body = """
                {"hostname":"%s","cpuPercent":1.0,"ramPercent":1.0,"diskPercent":1.0}
                """.formatted(hostname).strip();
        long staleTimestamp = Instant.now().getEpochSecond() - 3600;

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl("/api/metrics"), signedEntity(hostname, secret, body, staleTimestamp), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void revokedServerGets403SoAgentSelfDestructs() throws Exception {
        String hostname = "ingest-host-" + System.nanoTime();
        String secret = seedServer(hostname, "raw-secret-value-4", ServerStatus.REVOKED);

        String body = """
                {"hostname":"%s","cpuPercent":1.0,"ramPercent":1.0,"diskPercent":1.0}
                """.formatted(hostname).strip();
        long timestamp = Instant.now().getEpochSecond();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl("/api/metrics"), signedEntity(hostname, secret, body, timestamp), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
