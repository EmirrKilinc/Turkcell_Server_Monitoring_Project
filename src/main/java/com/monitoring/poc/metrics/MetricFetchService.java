package com.monitoring.poc.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.poc.entity.MetricFetchRequest;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.FetchRequestStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.FetchNowResponse;
import com.monitoring.poc.metrics.dto.MetricFetchRequestResponse;
import com.monitoring.poc.repository.MetricFetchRequestRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * "Fetch Now" runs over the agent's existing outbound-only heartbeat channel
 * (see AgentSyncService) rather than a live SSH exec or an agent-side
 * listening port - neither fits this system's security model (SSH creds are
 * never persisted past one-time provisioning; agents never accept inbound
 * connections). A request is queued here, the agent picks it up on its next
 * sync poll, and this service short-polls the DB for the result so the HTTP
 * caller gets a same-request answer in the common case.
 */
@Service
public class MetricFetchService {

    private static final Logger log = LoggerFactory.getLogger(MetricFetchService.class);

    private final MetricFetchRequestRepository metricFetchRequestRepository;
    private final MetricGroupRepository metricGroupRepository;
    private final MetricGroupItemRepository metricGroupItemRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final long pollIntervalMs;
    private final int pollMaxAttempts;
    private final long timeoutMinutes;

    public MetricFetchService(MetricFetchRequestRepository metricFetchRequestRepository,
                               MetricGroupRepository metricGroupRepository,
                               MetricGroupItemRepository metricGroupItemRepository,
                               UserRepository userRepository,
                               ObjectMapper objectMapper,
                               @Value("${app.metrics.fetch-poll-interval-ms}") long pollIntervalMs,
                               @Value("${app.metrics.fetch-poll-max-attempts}") int pollMaxAttempts,
                               @Value("${app.metrics.fetch-timeout-minutes}") long timeoutMinutes) {
        this.metricFetchRequestRepository = metricFetchRequestRepository;
        this.metricGroupRepository = metricGroupRepository;
        this.metricGroupItemRepository = metricGroupItemRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.pollIntervalMs = pollIntervalMs;
        this.pollMaxAttempts = pollMaxAttempts;
        this.timeoutMinutes = timeoutMinutes;
    }

    /**
     * Inserts a PENDING row (committed immediately - JpaRepository.save is
     * self-transactional) then polls for its completion with short,
     * independent reads rather than holding one DB connection open for the
     * whole wait. Always targets every item in the group - the point of a
     * manual trigger is "give me everything right now".
     */
    public FetchNowResponse fetchNow(Long groupId, String requestingUsername) {
        MetricGroup group = metricGroupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Metrik grubu bulunamadi"));

        // Only APPROVED metrics are ever dispatched to an agent (AgentSyncService
        // applies the same filter independently) - a group made up entirely of
        // still-pending/rejected metrics has nothing fetchable right now. This
        // is also the sole safety gate for on-demand fetches now that every
        // metric is a command - there's no more type-based VIEWER carve-out.
        List<MetricGroupItem> items = metricGroupItemRepository.findByGroup_Id(groupId).stream()
                .filter(item -> item.getMetricDefinition().getApprovalStatus() == ApprovalStatus.APPROVED)
                .toList();
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bu grupta calistirilabilir (onayli) metrik yok");
        }

        Long requestedByUserId = userRepository.findByUsername(requestingUsername)
                .map(u -> u.getId())
                .orElse(null);

        MetricFetchRequest fetchRequest = new MetricFetchRequest(
                groupId, group.getServer().getId(), requestedByUserId, FetchRequestStatus.PENDING);
        metricFetchRequestRepository.save(fetchRequest);

        return pollForCompletion(fetchRequest.getId());
    }

    private FetchNowResponse pollForCompletion(Long fetchRequestId) {
        for (int attempt = 0; attempt < pollMaxAttempts; attempt++) {
            Optional<MetricFetchRequest> current = metricFetchRequestRepository.findById(fetchRequestId);
            if (current.isPresent() && current.get().getStatus() != FetchRequestStatus.PENDING) {
                return toResponse(current.get());
            }
            sleep(pollIntervalMs);
        }
        return new FetchNowResponse("PENDING", fetchRequestId, null,
                "/api/metric-groups/fetch-requests/" + fetchRequestId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public MetricFetchRequestResponse getStatus(Long fetchRequestId) {
        MetricFetchRequest request = metricFetchRequestRepository.findById(fetchRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fetch istegi bulunamadi"));
        return new MetricFetchRequestResponse(request);
    }

    /**
     * Idempotent - only transitions rows still PENDING, so a duplicate or
     * late agent report (e.g. after the sweep already marked it TIMED_OUT)
     * can never clobber a settled row.
     */
    @Transactional
    public void completeFetchRequest(Long fetchRequestId, String resultPayloadJson) {
        metricFetchRequestRepository.findById(fetchRequestId).ifPresent(request -> {
            if (request.getStatus() != FetchRequestStatus.PENDING) {
                return;
            }
            request.setStatus(FetchRequestStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            request.setResultPayload(resultPayloadJson);
            metricFetchRequestRepository.save(request);
        });
    }

    /**
     * Safety net for a dead/unreachable agent: without this, a PENDING row
     * whose agent never reports back would stay PENDING forever.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void sweepStaleRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<MetricFetchRequest> stale = metricFetchRequestRepository
                .findByStatusAndRequestedAtBefore(FetchRequestStatus.PENDING, cutoff);
        for (MetricFetchRequest request : stale) {
            request.setStatus(FetchRequestStatus.TIMED_OUT);
            metricFetchRequestRepository.save(request);
        }
        if (!stale.isEmpty()) {
            log.info("{} bekleyen fetch istegi zaman asimina ugradi", stale.size());
        }
    }

    private FetchNowResponse toResponse(MetricFetchRequest request) {
        Object parsedPayload = parse(request.getResultPayload());
        return new FetchNowResponse(request.getStatus().name(), request.getId(), parsedPayload, null);
    }

    private Object parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
