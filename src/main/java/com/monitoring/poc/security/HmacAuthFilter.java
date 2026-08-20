package com.monitoring.poc.security;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.repository.ServerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Validates HMAC-SHA256(secretKey, timestamp + rawBody) on POST /api/metrics
 * (legacy system-stats ingestion) and on any method under /api/agent/**
 * (custom-metric sync protocol - GET /sync and POST /results). Signature =
 * hex(HMAC-SHA256). Anti-replay: timestamp must be within the configured
 * window of "now". A REVOKED server yields 403 - this is exactly what makes
 * an evicted agent self-terminate.
 */
public class HmacAuthFilter extends OncePerRequestFilter {

    public static final String SERVER_ID_ATTRIBUTE = "hmac.serverId";

    private static final Set<String> HMAC_EXACT_POST_PATHS = Set.of("/api/metrics");
    private static final String AGENT_PATH_PREFIX = "/api/agent/";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_HOSTNAME = "X-Server-Hostname";

    private final ServerRepository serverRepository;
    private final CryptoUtil cryptoUtil;
    private final long windowSeconds;

    public HmacAuthFilter(ServerRepository serverRepository, CryptoUtil cryptoUtil, long windowSeconds) {
        this.serverRepository = serverRepository;
        this.cryptoUtil = cryptoUtil;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        boolean legacyIngest = "POST".equalsIgnoreCase(request.getMethod()) && HMAC_EXACT_POST_PATHS.contains(path);
        boolean agentSync = path.startsWith(AGENT_PATH_PREFIX);
        return !(legacyIngest || agentSync);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String timestampHeader = request.getHeader(HEADER_TIMESTAMP);
        String signatureHeader = request.getHeader(HEADER_SIGNATURE);
        String hostnameHeader = request.getHeader(HEADER_HOSTNAME);

        if (timestampHeader == null || signatureHeader == null || hostnameHeader == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Eksik HMAC basliklari (X-Timestamp/X-Signature/X-Server-Hostname)");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Gecersiz timestamp");
            return;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > windowSeconds) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Timestamp izin verilen pencerenin disinda (olasi replay)");
            return;
        }

        Optional<Server> serverOpt = serverRepository.findByHostname(hostnameHeader);
        if (serverOpt.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Bilinmeyen sunucu");
            return;
        }

        Server server = serverOpt.get();
        if (server.getStatus() == ServerStatus.REVOKED) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "Sunucu izlemeden kaldirildi, secret key iptal edildi");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String rawBody = cachedRequest.getCachedBodyAsString();

        String secretKey = cryptoUtil.decrypt(server.getSecretKeyEncrypted());
        String expectedSignature = computeHmac(secretKey, timestampHeader + rawBody);

        if (!constantTimeEquals(expectedSignature, signatureHeader)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Imza gecersiz");
            return;
        }

        cachedRequest.setAttribute(SERVER_ID_ATTRIBUTE, server.getId());
        filterChain.doFilter(cachedRequest, response);
    }

    private String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
