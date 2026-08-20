package com.monitoring.poc.configs.dto;

import com.monitoring.poc.entity.TrackedConfigFile;

import java.time.LocalDateTime;

public class TrackedConfigFileResponse {

    private Long id;
    private Long serverId;
    private String serverHostname;
    private String filePath;
    private String fileLabel;
    private Integer checkIntervalSeconds;
    private LocalDateTime lastCheckedAt;
    private String currentHash;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private Boolean forceCheckRequested;
    private String baselineAcceptedBy;
    private LocalDateTime baselineAcceptedAt;

    public TrackedConfigFileResponse() {
    }

    public TrackedConfigFileResponse(TrackedConfigFile e) {
        this.id = e.getId();
        this.serverId = e.getServer().getId();
        this.serverHostname = e.getServer().getHostname();
        this.filePath = e.getFilePath();
        this.fileLabel = e.getFileLabel();
        this.checkIntervalSeconds = e.getCheckIntervalSeconds();
        this.lastCheckedAt = e.getLastCheckedAt();
        this.currentHash = e.getCurrentHash();
        this.status = e.getStatus().name();
        this.createdBy = e.getCreatedBy();
        this.createdAt = e.getCreatedAt();
        this.forceCheckRequested = e.getForceCheckRequested();
        this.baselineAcceptedBy = e.getBaselineAcceptedBy();
        this.baselineAcceptedAt = e.getBaselineAcceptedAt();
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileLabel() {
        return fileLabel;
    }

    public void setFileLabel(String fileLabel) {
        this.fileLabel = fileLabel;
    }

    public Integer getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public void setCheckIntervalSeconds(Integer checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getForceCheckRequested() {
        return forceCheckRequested;
    }

    public void setForceCheckRequested(Boolean forceCheckRequested) {
        this.forceCheckRequested = forceCheckRequested;
    }

    public String getBaselineAcceptedBy() {
        return baselineAcceptedBy;
    }

    public void setBaselineAcceptedBy(String baselineAcceptedBy) {
        this.baselineAcceptedBy = baselineAcceptedBy;
    }

    public LocalDateTime getBaselineAcceptedAt() {
        return baselineAcceptedAt;
    }

    public void setBaselineAcceptedAt(LocalDateTime baselineAcceptedAt) {
        this.baselineAcceptedAt = baselineAcceptedAt;
    }
}
