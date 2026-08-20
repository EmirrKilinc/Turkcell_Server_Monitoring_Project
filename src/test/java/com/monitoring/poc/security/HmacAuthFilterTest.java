package com.monitoring.poc.security;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.repository.ServerRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HmacAuthFilterTest {

    private static final String SECRET = "test-server-secret";
    private static final String HOSTNAME = "web-01";
    private static final long WINDOW_SECONDS = 300;

    private ServerRepository serverRepository;
    private CryptoUtil cryptoUtil;
    private HmacAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerRepository.class);
        cryptoUtil = mock(CryptoUtil.class);
        when(cryptoUtil.decrypt(any())).thenReturn(SECRET);
        filter = new HmacAuthFilter(serverRepository, cryptoUtil, WINDOW_SECONDS);
        chain = mock(FilterChain.class);
    }

    private Server activeServer() {
        Server server = new Server(HOSTNAME, "10.0.0.5", "monitoring_user", "encrypted", ServerStatus.ACTIVE, null);
        server.setId(1L);
        return server;
    }

    private String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private MockHttpServletRequest ingestRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/metrics");
        request.setServletPath("/api/metrics");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void acceptsValidSignatureAndContinuesChain() throws Exception {
        when(serverRepository.findByHostname(HOSTNAME)).thenReturn(Optional.of(activeServer()));

        String body = "{\"hostname\":\"web-01\",\"cpuPercent\":10.0,\"ramPercent\":20.0,\"diskPercent\":30.0}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(SECRET, timestamp, body);

        MockHttpServletRequest request = ingestRequest(body);
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Server-Hostname", HOSTNAME);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse default, filter never touched it
    }

    @Test
    void rejectsMissingHeaders() throws Exception {
        MockHttpServletRequest request = ingestRequest("{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsExpiredTimestamp() throws Exception {
        when(serverRepository.findByHostname(HOSTNAME)).thenReturn(Optional.of(activeServer()));

        String body = "{}";
        long staleTimestamp = Instant.now().getEpochSecond() - 3600; // 1 hour old
        String signature = sign(SECRET, staleTimestamp, body);

        MockHttpServletRequest request = ingestRequest(body);
        request.addHeader("X-Timestamp", String.valueOf(staleTimestamp));
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Server-Hostname", HOSTNAME);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(serverRepository.findByHostname(HOSTNAME)).thenReturn(Optional.of(activeServer()));

        String body = "{}";
        long timestamp = Instant.now().getEpochSecond();

        MockHttpServletRequest request = ingestRequest(body);
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Signature", "deadbeef");
        request.addHeader("X-Server-Hostname", HOSTNAME);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void returns403ForRevokedServerEvenWithValidSignature() throws Exception {
        Server revoked = activeServer();
        revoked.setStatus(ServerStatus.REVOKED);
        when(serverRepository.findByHostname(HOSTNAME)).thenReturn(Optional.of(revoked));

        String body = "{}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(SECRET, timestamp, body);

        MockHttpServletRequest request = ingestRequest(body);
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Server-Hostname", HOSTNAME);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void ignoresNonIngestRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/servers");
        request.setServletPath("/api/servers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void rejectsUnsignedGetRequestToAgentSyncEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/metrics/sync");
        request.setServletPath("/api/agent/metrics/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void acceptsValidSignatureOnAgentSyncGetRequest() throws Exception {
        when(serverRepository.findByHostname(HOSTNAME)).thenReturn(Optional.of(activeServer()));

        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(SECRET, timestamp, "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/agent/metrics/sync");
        request.setServletPath("/api/agent/metrics/sync");
        request.addHeader("X-Timestamp", String.valueOf(timestamp));
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Server-Hostname", HOSTNAME);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void appliesHmacCheckToAgentResultsPostRequestsToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/metrics/results");
        request.setServletPath("/api/agent/metrics/results");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
