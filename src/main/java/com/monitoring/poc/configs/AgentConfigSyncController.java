package com.monitoring.poc.configs;

import com.monitoring.poc.configs.dto.AgentConfigReportBatchRequest;
import com.monitoring.poc.configs.dto.AgentConfigSyncResponse;
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
 * Agent-facing config-drift sync protocol. Reuses the exact same
 * HmacAuthFilter gate as AgentSyncController - any path under /api/agent/
 * is already covered by its shouldNotFilter() prefix match and by
 * SecurityConfig's permitAll() on /api/agent/**, so no security config
 * changes were needed to add this controller.
 */
@RestController
@RequestMapping("/api/agent/configs")
public class AgentConfigSyncController {

    private final ConfigTrackerService configTrackerService;

    public AgentConfigSyncController(ConfigTrackerService configTrackerService) {
        this.configTrackerService = configTrackerService;
    }

    @GetMapping("/sync")
    public ResponseEntity<AgentConfigSyncResponse> sync(HttpServletRequest httpRequest) {
        Long serverId = (Long) httpRequest.getAttribute(HmacAuthFilter.SERVER_ID_ATTRIBUTE);
        return ResponseEntity.ok(configTrackerService.buildSyncResponse(serverId));
    }

    @PostMapping("/report")
    public ResponseEntity<Void> report(@Valid @RequestBody AgentConfigReportBatchRequest request,
                                        HttpServletRequest httpRequest) {
        Long serverId = (Long) httpRequest.getAttribute(HmacAuthFilter.SERVER_ID_ATTRIBUTE);
        request.getReports().forEach(report -> configTrackerService.recordReport(serverId, report));
        return ResponseEntity.ok().build();
    }
}
