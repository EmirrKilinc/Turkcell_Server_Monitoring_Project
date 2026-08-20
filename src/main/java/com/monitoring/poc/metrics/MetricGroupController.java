package com.monitoring.poc.metrics;

import com.monitoring.poc.metrics.dto.CustomMetricLogResponse;
import com.monitoring.poc.metrics.dto.FetchNowResponse;
import com.monitoring.poc.metrics.dto.MetricFetchRequestResponse;
import com.monitoring.poc.metrics.dto.MetricGroupDetailResponse;
import com.monitoring.poc.metrics.dto.MetricGroupItemRequest;
import com.monitoring.poc.metrics.dto.MetricGroupItemResponse;
import com.monitoring.poc.metrics.dto.MetricGroupRequest;
import com.monitoring.poc.metrics.dto.MetricGroupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metric-groups")
public class MetricGroupController {


    private final MetricGroupService metricGroupService;
    private final MetricFetchService metricFetchService;

    public MetricGroupController(MetricGroupService metricGroupService, MetricFetchService metricFetchService) {
        this.metricGroupService = metricGroupService;
        this.metricFetchService = metricFetchService;
    }

    @GetMapping
    public ResponseEntity<List<MetricGroupResponse>> listByServer(@RequestParam Long serverId) {
        return ResponseEntity.ok(metricGroupService.listByServer(serverId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetricGroupDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(metricGroupService.getDetail(id));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<CustomMetricLogResponse>> getLogs(@PathVariable Long id) {
        return ResponseEntity.ok(metricGroupService.getLogs(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MetricGroupResponse> create(@Valid @RequestBody MetricGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metricGroupService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MetricGroupResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody MetricGroupRequest request) {
        return ResponseEntity.ok(metricGroupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        metricGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MetricGroupItemResponse> addItem(@PathVariable Long id,
                                                             @Valid @RequestBody MetricGroupItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metricGroupService.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MetricGroupItemResponse> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                                                @Valid @RequestBody MetricGroupItemRequest request) {
        return ResponseEntity.ok(metricGroupService.updateItemOverride(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        metricGroupService.removeItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Open to every authenticated role, VIEWER included - every metric is
     * now a command, and only APPROVED definitions are ever dispatched
     * (enforced independently in MetricFetchService/AgentSyncService), so
     * the approval workflow is the actual safety gate here, not the role.
     */
    @PostMapping("/{id}/fetch-now")
    public ResponseEntity<FetchNowResponse> fetchNow(@PathVariable Long id, Authentication authentication) {
        FetchNowResponse response = metricFetchService.fetchNow(id, authentication.getName());
        HttpStatus status = "PENDING".equals(response.getStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/fetch-requests/{fetchRequestId}")
    public ResponseEntity<MetricFetchRequestResponse> getFetchStatus(@PathVariable Long fetchRequestId) {
        return ResponseEntity.ok(metricFetchService.getStatus(fetchRequestId));
    }
}
