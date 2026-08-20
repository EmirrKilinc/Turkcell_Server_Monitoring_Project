package com.monitoring.poc.servers;

import com.monitoring.poc.servers.dto.ProvisioningScriptRequest;
import com.monitoring.poc.servers.dto.ProvisioningScriptResponse;
import com.monitoring.poc.servers.dto.ProvisioningScriptUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provisioning-scripts")
@PreAuthorize("hasRole('ADMIN')")
public class ServerProvisioningScriptController {

    private final ServerProvisioningScriptService service;

    public ServerProvisioningScriptController(ServerProvisioningScriptService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ProvisioningScriptResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping
    public ResponseEntity<ProvisioningScriptResponse> create(@Valid @RequestBody ProvisioningScriptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProvisioningScriptResponse> update(@PathVariable Long id,
                                                                @RequestBody ProvisioningScriptUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/move-up")
    public ResponseEntity<ProvisioningScriptResponse> moveUp(@PathVariable Long id) {
        return ResponseEntity.ok(service.moveUp(id));
    }

    @PostMapping("/{id}/move-down")
    public ResponseEntity<ProvisioningScriptResponse> moveDown(@PathVariable Long id) {
        return ResponseEntity.ok(service.moveDown(id));
    }
}
