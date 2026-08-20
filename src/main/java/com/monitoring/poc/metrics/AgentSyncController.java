package com.monitoring.poc.metrics;

import com.monitoring.poc.metrics.dto.AgentResultsRequest;
import com.monitoring.poc.metrics.dto.AgentSyncResponse;
import com.monitoring.poc.security.HmacAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing sync protocol, HMAC-authenticated exactly like the legacy
 * {@code POST /api/metrics} ingestion (see HmacAuthFilter) - signature,
 * anti-replay and revoked-server checks all happen in the filter before
 * these methods ever run.
 */
@RestController
@RequestMapping("/api/agent/metrics")
public class AgentSyncController {

    private final AgentSyncService agentSyncService;

    public AgentSyncController(AgentSyncService agentSyncService) {
        this.agentSyncService = agentSyncService;
    }

    @GetMapping("/sync")
    public ResponseEntity<AgentSyncResponse> sync(HttpServletRequest httpRequest) {
        Long serverId = (Long) httpRequest.getAttribute(HmacAuthFilter.SERVER_ID_ATTRIBUTE);
        return ResponseEntity.ok(agentSyncService.buildSyncResponse(serverId));
    }

    @PostMapping("/results")
    public ResponseEntity<Void> results(@Valid @RequestBody AgentResultsRequest request, HttpServletRequest httpRequest) {
        Long serverId = (Long) httpRequest.getAttribute(HmacAuthFilter.SERVER_ID_ATTRIBUTE);
        agentSyncService.ingestResults(serverId, request);
        return ResponseEntity.ok().build();
    }
}
