package com.monitoring.poc.configs;

import com.monitoring.poc.configs.dto.ConfigFileDiffResponse;
import com.monitoring.poc.configs.dto.ConfigFileHistorySummaryResponse;
import com.monitoring.poc.configs.dto.TrackedConfigFileRequest;
import com.monitoring.poc.configs.dto.TrackedConfigFileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/configs/tracked-files")
public class ConfigTrackerController {

    private final ConfigTrackerService configTrackerService;

    public ConfigTrackerController(ConfigTrackerService configTrackerService) {
        this.configTrackerService = configTrackerService;
    }

    @GetMapping
    public ResponseEntity<List<TrackedConfigFileResponse>> listByServer(@RequestParam Long serverId) {
        return ResponseEntity.ok(configTrackerService.listByServer(serverId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TrackedConfigFileResponse> create(@Valid @RequestBody TrackedConfigFileRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configTrackerService.create(request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        configTrackerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ConfigFileHistorySummaryResponse>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(configTrackerService.getHistory(id));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<ConfigFileDiffResponse> getDiff(@PathVariable Long id,
                                                            @RequestParam Integer v1,
                                                            @RequestParam Integer v2) {
        return ResponseEntity.ok(configTrackerService.getDiff(id, v1, v2));
    }

    @PostMapping("/{id}/check-now")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TrackedConfigFileResponse> checkNow(@PathVariable Long id) {
        return ResponseEntity.ok(configTrackerService.checkNow(id));
    }

    @PostMapping("/{id}/accept-baseline")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TrackedConfigFileResponse> acceptBaseline(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(configTrackerService.acceptBaseline(id, authentication.getName()));
    }
}
