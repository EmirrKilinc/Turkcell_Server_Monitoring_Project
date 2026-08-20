package com.monitoring.poc.metrics;

import com.monitoring.poc.AbstractIntegrationTest;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricFetchRequest;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.enums.FetchRequestStatus;
import com.monitoring.poc.enums.MetricValueType;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.metrics.dto.FetchNowResponse;
import com.monitoring.poc.repository.CustomMetricLogRepository;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricFetchRequestRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.security.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSyncIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private MetricDefinitionRepository metricDefinitionRepository;

    @Autowired
    private MetricGroupRepository metricGroupRepository;

    @Autowired
    private MetricGroupItemRepository metricGroupItemRepository;

    @Autowired
    private CustomMetricLogRepository customMetricLogRepository;

    @Autowired
    private MetricFetchRequestRepository metricFetchRequestRepository;

    @Autowired
    private MetricFetchService metricFetchService;

    @Autowired
    private CryptoUtil cryptoUtil;

    private Server seedServer(String hostname, String rawSecret, ServerStatus status) {
        Server server = new Server(hostname, "10.6.6.6", "monitoring_user", cryptoUtil.encrypt(rawSecret), status, null);
        serverRepository.save(server);
        return server;
    }

    private MetricDefinition seedDefinition(String metricKey) {
        MetricDefinition definition = new MetricDefinition("Test Metric", metricKey, "APPLICATION",
                "curl -sf http://127.0.0.1/x", 3, MetricValueType.RAW, null, null);
        metricDefinitionRepository.save(definition);
        return definition;
    }

    private String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private HttpEntity<Void> signedGetEntity(String hostname, String secret, long timestamp) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Timestamp", String.valueOf(timestamp));
        headers.add("X-Signature", sign(secret, timestamp, ""));
        headers.add("X-Server-Hostname", hostname);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<String> signedPostEntity(String hostname, String secret, String body, long timestamp) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Timestamp", String.valueOf(timestamp));
        headers.add("X-Signature", sign(secret, timestamp, body));
        headers.add("X-Server-Hostname", hostname);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void syncReturnsDueItemAndOmitsNotYetDueItem() throws Exception {
        String secret = "raw-sync-secret-1";
        Server server = seedServer("sync-host-" + System.nanoTime(), secret, ServerStatus.ACTIVE);
        MetricDefinition dueDef = seedDefinition("due_metric_" + System.nanoTime());
        MetricDefinition notDueDef = seedDefinition("not_due_metric_" + System.nanoTime());

        MetricGroup group = new MetricGroup(server, "Sync Test Group", ExecutionMode.PERIODIC, 3600);
        metricGroupRepository.save(group);

        MetricGroupItem dueItem = new MetricGroupItem(group, dueDef, null);
        metricGroupItemRepository.save(dueItem);

        MetricGroupItem notDueItem = new MetricGroupItem(group, notDueDef, null);
        notDueItem.setLastRunAt(LocalDateTime.now());
        metricGroupItemRepository.save(notDueItem);

        long timestamp = Instant.now().getEpochSecond();
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/agent/metrics/sync"), HttpMethod.GET,
                signedGetEntity(server.getHostname(), secret, timestamp), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> dueItems = (List<?>) response.getBody().get("dueItems");
        assertThat(dueItems).hasSize(1);
    }

    @Test
    void syncOmitsItemsWhoseDefinitionIsPendingApproval() throws Exception {
        String secret = "raw-sync-secret-approval";
        Server server = seedServer("sync-host-approval-" + System.nanoTime(), secret, ServerStatus.ACTIVE);
        MetricDefinition pendingDef = seedDefinition("pending_metric_" + System.nanoTime());
        pendingDef.setApprovalStatus(com.monitoring.poc.enums.ApprovalStatus.PENDING_APPROVAL);
        metricDefinitionRepository.save(pendingDef);

        MetricGroup group = new MetricGroup(server, "Pending Approval Sync Group", ExecutionMode.PERIODIC, 3600);
        metricGroupRepository.save(group);
        MetricGroupItem item = new MetricGroupItem(group, pendingDef, null);
        metricGroupItemRepository.save(item);

        long timestamp = Instant.now().getEpochSecond();
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/agent/metrics/sync"), HttpMethod.GET,
                signedGetEntity(server.getHostname(), secret, timestamp), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> dueItems = (List<?>) response.getBody().get("dueItems");
        assertThat(dueItems).isEmpty();
    }

    @Test
    void resultsPersistLogAndBumpLastRunAt() throws Exception {
        String secret = "raw-sync-secret-2";
        Server server = seedServer("sync-host-" + System.nanoTime(), secret, ServerStatus.ACTIVE);
        MetricDefinition definition = seedDefinition("results_metric_" + System.nanoTime());
        MetricGroup group = new MetricGroup(server, "Results Test Group", ExecutionMode.PERIODIC, 15);
        metricGroupRepository.save(group);
        MetricGroupItem item = new MetricGroupItem(group, definition, null);
        metricGroupItemRepository.save(item);

        String body = "{\"periodicResults\":[{\"groupItemId\":" + item.getId()
                + ",\"metricKey\":\"" + definition.getMetricKey() + "\",\"success\":true,\"value\":42,\"error\":null}],"
                + "\"fetchResults\":[]}";
        long timestamp = Instant.now().getEpochSecond();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl("/api/agent/metrics/results"),
                signedPostEntity(server.getHostname(), secret, body, timestamp), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(customMetricLogRepository.findTop50ByGroupIdOrderByCreatedAtDesc(group.getId())).hasSize(1);
        assertThat(metricGroupItemRepository.findById(item.getId()).orElseThrow().getLastRunAt()).isNotNull();
    }

    @Test
    void revokedServerGets403OnSync() throws Exception {
        String secret = "raw-sync-secret-3";
        Server server = seedServer("sync-host-" + System.nanoTime(), secret, ServerStatus.REVOKED);

        long timestamp = Instant.now().getEpochSecond();
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/agent/metrics/sync"), HttpMethod.GET,
                signedGetEntity(server.getHostname(), secret, timestamp), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unsignedSyncRequestIs401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl("/api/agent/metrics/sync"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Runs fetchNow on a background thread (simulating the HTTP caller
     * blocked in its poll loop) while concurrently POSTing a signed agent
     * result for that same fetch request - proves the two sides of "Fetch
     * Now" (HTTP caller waiting + agent reporting over its normal heartbeat)
     * actually meet up within the bounded poll window.
     */
    @Test
    void fetchNowEndToEndAgentReportsWithinPollWindow() throws Exception {
        String secret = "raw-fetch-secret-1";
        Server server = seedServer("fetch-host-" + System.nanoTime(), secret, ServerStatus.ACTIVE);
        MetricDefinition definition = seedDefinition("fetch_metric_" + System.nanoTime());
        MetricGroup group = new MetricGroup(server, "Fetch Test Group", ExecutionMode.ON_DEMAND, 15);
        metricGroupRepository.save(group);
        MetricGroupItem item = new MetricGroupItem(group, definition, null);
        metricGroupItemRepository.save(item);

        CompletableFuture<FetchNowResponse> future = CompletableFuture.supplyAsync(
                () -> metricFetchService.fetchNow(group.getId(), "admin"));

        MetricFetchRequest fetchRequest = awaitPendingFetchRequest(server.getId());

        String body = "{\"periodicResults\":[],\"fetchResults\":[{\"fetchRequestId\":" + fetchRequest.getId()
                + ",\"results\":[{\"groupItemId\":" + item.getId() + ",\"metricKey\":\"" + definition.getMetricKey()
                + "\",\"success\":true,\"value\":7,\"error\":null}]}]}";
        long timestamp = Instant.now().getEpochSecond();

        ResponseEntity<Void> resultsResponse = restTemplate.postForEntity(
                baseUrl("/api/agent/metrics/results"),
                signedPostEntity(server.getHostname(), secret, body, timestamp), Void.class);
        assertThat(resultsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        FetchNowResponse response = future.get(15, TimeUnit.SECONDS);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getFetchRequestId()).isEqualTo(fetchRequest.getId());
    }

    private MetricFetchRequest awaitPendingFetchRequest(Long serverId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<MetricFetchRequest> pending = metricFetchRequestRepository
                    .findByServerIdAndStatus(serverId, FetchRequestStatus.PENDING);
            if (!pending.isEmpty()) {
                return pending.get(0);
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Fetch istegi zamaninda olusturulmadi");
    }
}
