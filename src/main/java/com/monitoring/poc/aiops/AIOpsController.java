package com.monitoring.poc.aiops;

import com.monitoring.poc.aiops.dto.AIOpsChatRequest;
import com.monitoring.poc.aiops.dto.AIOpsChatResponse;
import com.monitoring.poc.aiops.dto.AIOpsConfigDto;
import com.monitoring.poc.aiops.dto.AnomalyCardDto;
import com.monitoring.poc.aiops.dto.AvailableMetricGroupDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aiops")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIOpsController {

    private final AIOpsService aiOpsService;

    public AIOpsController(AIOpsService aiOpsService) {
        this.aiOpsService = aiOpsService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AIOpsChatResponse> chat(@Valid @RequestBody AIOpsChatRequest request) {
        return ResponseEntity.ok(aiOpsService.chat(request));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<AnomalyCardDto>> anomalies() {
        return ResponseEntity.ok(aiOpsService.getAnomalies());
    }

    @GetMapping("/available-groups")
    public ResponseEntity<List<AvailableMetricGroupDto>> availableGroups() {
        return ResponseEntity.ok(aiOpsService.getAvailableGroups());
    }

    @GetMapping("/config")
    public ResponseEntity<AIOpsConfigDto> getConfig() {
        return ResponseEntity.ok(aiOpsService.getConfig());
    }

    @PostMapping("/config")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<AIOpsConfigDto> saveConfig(@Valid @RequestBody AIOpsConfigDto request) {
        return ResponseEntity.ok(aiOpsService.saveConfig(request));
    }
}
