package com.monitoring.poc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "metrics", indexes = {
        @Index(name = "idx_metrics_hostname", columnList = "hostname"),
        @Index(name = "idx_metrics_created_at", columnList = "createdAt")
})
public class MetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_id")
    private Long serverId;

    @Column(nullable = false, length = 150)
    private String hostname;

    @Column(nullable = false)
    private Double cpuPercent;

    @Column(nullable = false)
    private Double ramPercent;

    @Column(nullable = false)
    private Double diskPercent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public MetricEntity() {
    }

    public MetricEntity(String hostname, Double cpuPercent, Double ramPercent, Double diskPercent, LocalDateTime createdAt) {
        this.hostname = hostname;
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
        this.diskPercent = diskPercent;
        this.createdAt = createdAt;
    }

    public MetricEntity(Long serverId, String hostname, Double cpuPercent, Double ramPercent, Double diskPercent, LocalDateTime createdAt) {
        this.serverId = serverId;
        this.hostname = hostname;
        this.cpuPercent = cpuPercent;
        this.ramPercent = ramPercent;
        this.diskPercent = diskPercent;
        this.createdAt = createdAt;
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
