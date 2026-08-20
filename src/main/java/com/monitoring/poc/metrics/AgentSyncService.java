package com.monitoring.poc.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.monitoring.poc.entity.CustomMetricLog;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricFetchRequest;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.enums.FetchRequestStatus;
import com.monitoring.poc.metrics.dto.AgentFetchRequestDto;
import com.monitoring.poc.metrics.dto.AgentFetchResultDto;
import com.monitoring.poc.metrics.dto.AgentMetricItemDto;
import com.monitoring.poc.metrics.dto.AgentResultsRequest;
import com.monitoring.poc.metrics.dto.AgentSyncResponse;
import com.monitoring.poc.metrics.dto.MetricResultDto;
import com.monitoring.poc.repository.CustomMetricLogRepository;
import com.monitoring.poc.repository.MetricFetchRequestRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent-facing half of the sync protocol: builds "what's due to run right
 * now" on every heartbeat and persists whatever the agent reports back on
 * its next one. This is the entire mechanism behind PERIODIC scheduling and
 * "Fetch Now" - there is no separate push channel into the agent.
 */
@Service
public class AgentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentSyncService.class);

    private final MetricGroupItemRepository metricGroupItemRepository;
    private final MetricFetchRequestRepository metricFetchRequestRepository;
    private final CustomMetricLogRepository customMetricLogRepository;
    private final MetricFetchService metricFetchService;
    private final ObjectMapper objectMapper;

    public AgentSyncService(MetricGroupItemRepository metricGroupItemRepository,
                             MetricFetchRequestRepository metricFetchRequestRepository,
                             CustomMetricLogRepository customMetricLogRepository,
                             MetricFetchService metricFetchService,
                             ObjectMapper objectMapper) {
        this.metricGroupItemRepository = metricGroupItemRepository;
        this.metricFetchRequestRepository = metricFetchRequestRepository;
        this.customMetricLogRepository = customMetricLogRepository;
        this.metricFetchService = metricFetchService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AgentSyncResponse buildSyncResponse(Long serverId) {
        // Belt-and-suspenders: addItem already blocks attaching a non-APPROVED
        // definition, but a definition can be rejected *after* being attached
        // (no "revoke approval" flow exists) - filtering here is what actually
        // keeps an unapproved metric from ever reaching the agent.
        List<MetricGroupItem> items = metricGroupItemRepository.findAllForServer(serverId).stream()
                .filter(item -> item.getMetricDefinition().getApprovalStatus() == ApprovalStatus.APPROVED)
                .toList();
        LocalDateTime now = LocalDateTime.now();

        List<AgentMetricItemDto> dueItems = items.stream()
                .filter(item -> MetricGroupService.resolveEffectiveMode(item) == ExecutionMode.PERIODIC)
                .filter(item -> isDue(item, now))
                .map(this::toItemDto)
                .toList();

        List<MetricFetchRequest> pending = metricFetchRequestRepository
                .findByServerIdAndStatus(serverId, FetchRequestStatus.PENDING);

        List<AgentFetchRequestDto> pendingFetchRequests = pending.stream()
                .map(request -> {
                    List<AgentMetricItemDto> groupItems = items.stream()
                            .filter(item -> item.getGroup().getId().equals(request.getGroupId()))
                            .map(this::toItemDto)
                            .toList();
                    return new AgentFetchRequestDto(request.getId(), request.getGroupId(), groupItems);
                })
                .toList();

        return new AgentSyncResponse(dueItems, pendingFetchRequests);
    }

    @Transactional
    public void ingestResults(Long serverId, AgentResultsRequest request) {
        for (MetricResultDto result : request.getPeriodicResults()) {
            recordPeriodicResult(serverId, result);
        }
        for (AgentFetchResultDto fetchResult : request.getFetchResults()) {
            recordFetchResult(serverId, fetchResult);
        }
    }

    private void recordPeriodicResult(Long serverId, MetricResultDto result) {
        Optional<MetricGroupItem> itemOpt = metricGroupItemRepository.findById(result.getGroupItemId());
        if (itemOpt.isEmpty()) {
            log.warn("Agent bilinmeyen grup metrigi icin sonuc gonderdi: groupItemId={}", result.getGroupItemId());
            return;
        }
        MetricGroupItem item = itemOpt.get();
        writeLog(serverId, item.getGroup().getId(), item.getId(), result);
        item.setLastRunAt(LocalDateTime.now());
        metricGroupItemRepository.save(item);
    }

    private void recordFetchResult(Long serverId, AgentFetchResultDto fetchResult) {
        MetricFetchRequest fetchRequest = metricFetchRequestRepository.findById(fetchResult.getFetchRequestId())
                .orElse(null);
        if (fetchRequest == null || !fetchRequest.getServerId().equals(serverId)) {
            log.warn("Agent baska bir sunucuya ait fetch istegi icin sonuc gonderdi: fetchRequestId={}",
                    fetchResult.getFetchRequestId());
            return;
        }

        List<Map<String, Object>> aggregated = fetchResult.getResults().stream()
                .map(result -> {
                    metricGroupItemRepository.findById(result.getGroupItemId())
                            .ifPresent(item -> writeLog(serverId, fetchRequest.getGroupId(), item.getId(), result));
                    return toResultMap(result);
                })
                .toList();

        try {
            String aggregatedJson = objectMapper.writeValueAsString(aggregated);
            metricFetchService.completeFetchRequest(fetchRequest.getId(), aggregatedJson);
        } catch (Exception e) {
            log.error("Fetch sonucu serilestirilemedi: fetchRequestId={}", fetchRequest.getId(), e);
        }
    }

    private void writeLog(Long serverId, Long groupId, Long groupItemId, MetricResultDto result) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(toResultMap(result));
        } catch (Exception e) {
            payloadJson = "{}";
        }
        CustomMetricLog metricLog = new CustomMetricLog(serverId, groupId, groupItemId, result.getMetricKey(),
                result.getSuccess(), payloadJson, LocalDateTime.now());
        customMetricLogRepository.save(metricLog);
    }

    private Map<String, Object> toResultMap(MetricResultDto result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", result.getValue());
        map.put("error", result.getError());
        return map;
    }

    private boolean isDue(MetricGroupItem item, LocalDateTime now) {
        if (item.getLastRunAt() == null) {
            return true;
        }
        int intervalSeconds = item.getGroup().getIntervalSeconds();
        return item.getLastRunAt().plusSeconds(intervalSeconds).isBefore(now)
                || item.getLastRunAt().plusSeconds(intervalSeconds).isEqual(now);
    }

    private AgentMetricItemDto toItemDto(MetricGroupItem item) {
        MetricDefinition definition = item.getMetricDefinition();
        return new AgentMetricItemDto(
                item.getId(),
                item.getGroup().getId(),
                definition.getId(),
                definition.getMetricKey(),
                "CUSTOM_COMMAND",
                buildCommandPayloadJson(definition)
        );
    }

    /**
     * Every metric is dispatched through agent.py's unmodified CUSTOM_COMMAND
     * collector - the "type" sent above is a fixed literal, not a stored
     * value, so agent.py needs zero changes. This just reassembles the same
     * JSON shape it already expects from the structured columns.
     */
    private String buildCommandPayloadJson(MetricDefinition definition) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("command", definition.getCommand());
        node.put("timeoutSeconds", definition.getTimeoutSeconds());
        String agentValueType = switch (definition.getValueType()) {
            case NUMERIC -> "number";
            case LINE_COUNT -> "lineCount";
            case MATCH_COUNT -> "matchCount";
            case RAW -> null;
        };
        if (agentValueType != null) {
            node.put("valueType", agentValueType);
        }
        if (definition.getExtractPattern() != null && !definition.getExtractPattern().isBlank()) {
            node.put("extractPattern", definition.getExtractPattern());
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "commandPayload serilestirilemedi: metricKey=" + definition.getMetricKey(), e);
        }
    }
}
