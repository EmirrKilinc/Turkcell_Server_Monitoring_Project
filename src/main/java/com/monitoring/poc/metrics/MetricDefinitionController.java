package com.monitoring.poc.metrics;

import com.monitoring.poc.metrics.dto.MetricApprovalRequest;
import com.monitoring.poc.metrics.dto.MetricDefinitionRequest;
import com.monitoring.poc.metrics.dto.MetricDefinitionResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metric-definitions")
public class MetricDefinitionController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final MetricDefinitionService metricDefinitionService;

    public MetricDefinitionController(MetricDefinitionService metricDefinitionService) {
        this.metricDefinitionService = metricDefinitionService;
    }

    @GetMapping
    public ResponseEntity<List<MetricDefinitionResponse>> listAll() {
        return ResponseEntity.ok(metricDefinitionService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetricDefinitionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(metricDefinitionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<MetricDefinitionResponse> create(@Valid @RequestBody MetricDefinitionRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(metricDefinitionService.create(request, isAdmin(authentication), authentication.getName()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MetricDefinitionResponse> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(metricDefinitionService.approve(id, authentication.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MetricDefinitionResponse> reject(@PathVariable Long id,
                                                             @RequestBody MetricApprovalRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(metricDefinitionService.reject(id, authentication.getName(), request.getRejectionReason()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MetricDefinitionResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody MetricDefinitionRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(metricDefinitionService.update(id, request, isAdmin(authentication)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        metricDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
