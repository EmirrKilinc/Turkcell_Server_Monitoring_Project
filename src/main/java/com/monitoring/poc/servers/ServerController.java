package com.monitoring.poc.servers;

import com.monitoring.poc.servers.dto.DeleteServerRequest;
import com.monitoring.poc.servers.dto.ProvisionServerRequest;
import com.monitoring.poc.servers.dto.ServerResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final ServerService serverService;

    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @GetMapping
    public ResponseEntity<List<ServerResponse>> listServers() {
        return ResponseEntity.ok(serverService.listActive());
    }

    @PostMapping("/provision")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ServerResponse> provision(@Valid @RequestBody ProvisionServerRequest request,
                                                      Authentication authentication) {
        ServerResponse response = serverService.provision(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable Long id,
                                        @Valid @RequestBody DeleteServerRequest request,
                                        Authentication authentication) {
        serverService.remove(id, request.getPassword(), authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
