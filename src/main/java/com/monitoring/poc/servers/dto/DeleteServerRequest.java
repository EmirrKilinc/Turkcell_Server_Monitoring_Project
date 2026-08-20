package com.monitoring.poc.servers.dto;

import jakarta.validation.constraints.NotBlank;

public class DeleteServerRequest {

    @NotBlank
    private String password;

    public DeleteServerRequest() {
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
