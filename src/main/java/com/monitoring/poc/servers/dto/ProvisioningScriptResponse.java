package com.monitoring.poc.servers.dto;

import com.monitoring.poc.entity.ServerProvisioningScript;

import java.time.LocalDateTime;

public class ProvisioningScriptResponse {

    private Long id;
    private String commandLine;
    private String description;
    private Integer executionOrder;
    private Boolean isEnabled;
    private LocalDateTime createdAt;

    public ProvisioningScriptResponse() {
    }

    public ProvisioningScriptResponse(ServerProvisioningScript e) {
        this.id = e.getId();
        this.commandLine = e.getCommandLine();
        this.description = e.getDescription();
        this.executionOrder = e.getExecutionOrder();
        this.isEnabled = e.getIsEnabled();
        this.createdAt = e.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(Integer executionOrder) {
        this.executionOrder = executionOrder;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
