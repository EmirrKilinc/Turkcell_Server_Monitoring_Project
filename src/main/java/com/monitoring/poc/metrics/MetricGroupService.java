package com.monitoring.poc.metrics;

import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.CustomMetricLogResponse;
import com.monitoring.poc.metrics.dto.MetricGroupDetailResponse;
import com.monitoring.poc.metrics.dto.MetricGroupItemRequest;
import com.monitoring.poc.metrics.dto.MetricGroupItemResponse;
import com.monitoring.poc.metrics.dto.MetricGroupRequest;
import com.monitoring.poc.metrics.dto.MetricGroupResponse;
import com.monitoring.poc.repository.CustomMetricLogRepository;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.ServerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricGroupService {

    private final MetricGroupRepository metricGroupRepository;
    private final MetricGroupItemRepository metricGroupItemRepository;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final CustomMetricLogRepository customMetricLogRepository;
    private final ServerRepository serverRepository;

    public MetricGroupService(MetricGroupRepository metricGroupRepository,
                               MetricGroupItemRepository metricGroupItemRepository,
                               MetricDefinitionRepository metricDefinitionRepository,
                               CustomMetricLogRepository customMetricLogRepository,
                               ServerRepository serverRepository) {
        this.metricGroupRepository = metricGroupRepository;
        this.metricGroupItemRepository = metricGroupItemRepository;
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.customMetricLogRepository = customMetricLogRepository;
        this.serverRepository = serverRepository;
    }

    public List<MetricGroupResponse> listByServer(Long serverId) {
        return metricGroupRepository.findByServer_Id(serverId).stream()
                .map(MetricGroupResponse::new)
                .toList();
    }

    public MetricGroupDetailResponse getDetail(Long groupId) {
        MetricGroup group = findGroupOrThrow(groupId);
        List<MetricGroupItem> items = metricGroupItemRepository.findByGroup_Id(groupId);
        return new MetricGroupDetailResponse(group, items);
    }

    @Transactional
    public MetricGroupResponse create(MetricGroupRequest request) {
        Server server = serverRepository.findById(request.getServerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sunucu bulunamadi"));

        if (metricGroupRepository.existsByServer_IdAndName(request.getServerId(), request.getName())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu sunucuda (" + server.getHostname() + ") '" + request.getName() + "' adinda bir grup zaten var");
        }
        validateInterval(request.getIntervalSeconds());

        MetricGroup group = new MetricGroup(server, request.getName(), request.getDefaultExecutionMode(),
                request.getIntervalSeconds());
        metricGroupRepository.save(group);
        return new MetricGroupResponse(group);
    }

    @Transactional
    public MetricGroupResponse update(Long groupId, MetricGroupRequest request) {
        MetricGroup group = findGroupOrThrow(groupId);
        validateInterval(request.getIntervalSeconds());

        if (!group.getName().equals(request.getName())
                && metricGroupRepository.existsByServer_IdAndName(group.getServer().getId(), request.getName())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu sunucuda '" + request.getName() + "' adinda bir grup zaten var");
        }

        group.setName(request.getName());
        group.setDefaultExecutionMode(request.getDefaultExecutionMode());
        group.setIntervalSeconds(request.getIntervalSeconds());
        group.setUpdatedAt(LocalDateTime.now());
        metricGroupRepository.save(group);
        return new MetricGroupResponse(group);
    }

    @Transactional
    public void delete(Long groupId) {
        MetricGroup group = findGroupOrThrow(groupId);
        metricGroupRepository.delete(group);
    }

    @Transactional
    public MetricGroupItemResponse addItem(Long groupId, MetricGroupItemRequest request) {
        MetricGroup group = findGroupOrThrow(groupId);
        MetricDefinition definition = metricDefinitionRepository.findById(request.getMetricDefinitionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Metrik tanimi bulunamadi"));

        if (definition.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Sadece onaylanmis metrikler gruplara eklenebilir");
        }

        if (metricGroupItemRepository.existsByGroup_IdAndMetricDefinition_Id(groupId, definition.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu metrik zaten bu grupta");
        }

        MetricGroupItem item = new MetricGroupItem(group, definition, request.getOverrideExecutionMode());
        metricGroupItemRepository.save(item);
        return new MetricGroupItemResponse(item);
    }

    @Transactional
    public MetricGroupItemResponse updateItemOverride(Long groupId, Long itemId, MetricGroupItemRequest request) {
        MetricGroupItem item = findItemOrThrow(groupId, itemId);
        item.setOverrideExecutionMode(request.getOverrideExecutionMode());
        metricGroupItemRepository.save(item);
        return new MetricGroupItemResponse(item);
    }

    @Transactional
    public void removeItem(Long groupId, Long itemId) {
        MetricGroupItem item = findItemOrThrow(groupId, itemId);
        metricGroupItemRepository.delete(item);
    }

    public List<CustomMetricLogResponse> getLogs(Long groupId) {
        findGroupOrThrow(groupId);
        return customMetricLogRepository.findTop50ByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(CustomMetricLogResponse::new)
                .toList();
    }

    /**
     * Null override inherits the group's default - this is the single source
     * of truth for "what mode does this item actually run in", used both by
     * the agent-sync due-item filter and by the response DTO.
     */
    public static ExecutionMode resolveEffectiveMode(MetricGroupItem item) {
        return item.getOverrideExecutionMode() != null ? item.getOverrideExecutionMode() : item.getGroup().getDefaultExecutionMode();
    }

    private MetricGroup findGroupOrThrow(Long groupId) {
        return metricGroupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Metrik grubu bulunamadi"));
    }

    private MetricGroupItem findItemOrThrow(Long groupId, Long itemId) {
        MetricGroupItem item = metricGroupItemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Grup metrigi bulunamadi"));
        if (!item.getGroup().getId().equals(groupId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Grup metrigi bulunamadi");
        }
        return item;
    }

    private void validateInterval(Integer intervalSeconds) {
        if (intervalSeconds == null || intervalSeconds <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "interval_seconds pozitif olmalidir");
        }
    }
}
