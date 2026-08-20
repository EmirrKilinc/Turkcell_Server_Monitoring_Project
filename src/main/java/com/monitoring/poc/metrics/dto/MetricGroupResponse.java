package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.entity.MetricGroup;

import java.time.LocalDateTime;

public class MetricGroupResponse {

    private Long id;
    private Long serverId;
    private String serverHostname;
    private String name;
    private String defaultExecutionMode;
    private Integer intervalSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MetricGroupResponse() {
    }

    public MetricGroupResponse(MetricGroup e) {
        this.id = e.getId();
        this.serverId = e.getServer().getId();
        this.serverHostname = e.getServer().getHostname();
        this.name = e.getName();
        this.defaultExecutionMode = e.getDefaultExecutionMode().name();
        this.intervalSeconds = e.getIntervalSeconds();
        this.createdAt = e.getCreatedAt();
        this.updatedAt = e.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getServerHostname() {
        return serverHostname;
    }

    public void setServerHostname(String serverHostname) {
        this.serverHostname = serverHostname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultExecutionMode() {
        return defaultExecutionMode;
    }

    public void setDefaultExecutionMode(String defaultExecutionMode) {
        this.defaultExecutionMode = defaultExecutionMode;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Integer intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
