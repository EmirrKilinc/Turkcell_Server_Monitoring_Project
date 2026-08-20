package com.monitoring.poc.dto;

import com.monitoring.poc.entity.MetricEntity;

import java.time.LocalDateTime;

public class MetricResponse {

    private String hostname;
    private Double cpuPercent;
    private Double ramPercent;
    private Double diskPercent;
    private LocalDateTime createdAt;

    public MetricResponse() {
    }

    public MetricResponse(MetricEntity entity) {
        this.hostname = entity.getHostname();
        this.cpuPercent = entity.getCpuPercent();
        this.ramPercent = entity.getRamPercent();
        this.diskPercent = entity.getDiskPercent();
        this.createdAt = entity.getCreatedAt();
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Double getCpuPercent() {
        return cpuPercent;
    }

    public void setCpuPercent(Double cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    public Double getRamPercent() {
        return ramPercent;
    }

    public void setRamPercent(Double ramPercent) {
        this.ramPercent = ramPercent;
    }

    public Double getDiskPercent() {
        return diskPercent;
    }

    public void setDiskPercent(Double diskPercent) {
        this.diskPercent = diskPercent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
