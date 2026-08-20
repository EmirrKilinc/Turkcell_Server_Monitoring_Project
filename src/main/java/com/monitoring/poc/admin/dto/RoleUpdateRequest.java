package com.monitoring.poc.admin.dto;

import com.monitoring.poc.enums.Role;
import jakarta.validation.constraints.NotNull;

public class RoleUpdateRequest {

    @NotNull
    private Role role;

    public RoleUpdateRequest() {
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
