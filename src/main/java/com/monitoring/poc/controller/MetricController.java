package com.monitoring.poc.controller;

import com.monitoring.poc.dto.MetricRequest;
import com.monitoring.poc.dto.MetricResponse;
import com.monitoring.poc.entity.MetricEntity;
import com.monitoring.poc.repository.MetricRepository;
import com.monitoring.poc.security.HmacAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MetricController {

    private final MetricRepository metricRepository;

    public MetricController(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    /**
     * HMAC-authenticated ingestion (see HmacAuthFilter). Signature, anti-replay
     * and revoked-server checks all happen in the filter before this method
     * ever runs; the server id it resolved is handed over via a request
     * attribute so this stays a single DB write with no re-lookup.
     */
    @PostMapping("/metrics")
    public ResponseEntity<Void> saveMetric(@Valid @RequestBody MetricRequest request, HttpServletRequest httpRequest) {
        Long serverId = (Long) httpRequest.getAttribute(HmacAuthFilter.SERVER_ID_ATTRIBUTE);

        MetricEntity entity = new MetricEntity(
                serverId,
                request.getHostname(),
                request.getCpuPercent(),
                request.getRamPercent(),
                request.getDiskPercent(),
                LocalDateTime.now()
        );
        metricRepository.save(entity);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/metrics/{hostname}")
    public ResponseEntity<MetricResponse> getLatestMetric(@PathVariable String hostname) {
        MetricEntity latest = metricRepository.findFirstByHostnameOrderByCreatedAtDesc(hostname);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new MetricResponse(latest));
    }

    @GetMapping("/metrics/{hostname}/history")
    public ResponseEntity<List<MetricResponse>> getMetricHistory(@PathVariable String hostname) {
        List<MetricResponse> history = metricRepository
                .findTop10ByHostnameOrderByCreatedAtDesc(hostname)
                .stream()
                .map(MetricResponse::new)
                .toList();
        return ResponseEntity.ok(history);
    }
}
