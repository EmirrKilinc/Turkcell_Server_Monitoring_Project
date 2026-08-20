package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.enums.ExecutionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MetricGroupRequest {

    @NotNull
    private Long serverId;

    @NotBlank
    private String name;

    @NotNull
    private ExecutionMode defaultExecutionMode;

    @NotNull
    private Integer intervalSeconds;

    public MetricGroupRequest() {
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExecutionMode getDefaultExecutionMode() {
        return defaultExecutionMode;
    }

    public void setDefaultExecutionMode(ExecutionMode defaultExecutionMode) {
        this.defaultExecutionMode = defaultExecutionMode;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Integer intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
}
