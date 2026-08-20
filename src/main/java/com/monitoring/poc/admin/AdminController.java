package com.monitoring.poc.admin;

import com.monitoring.poc.admin.dto.RoleUpdateRequest;
import com.monitoring.poc.admin.dto.StatusUpdateRequest;
import com.monitoring.poc.admin.dto.UserSummaryResponse;
import com.monitoring.poc.enums.UserStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(adminService.listUsers(status));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<UserSummaryResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateStatus(id, request.getStatus()));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserSummaryResponse> updateRole(
            @PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateRole(id, request.getRole()));
    }
}
