package com.monitoring.poc.entity;

import com.monitoring.poc.enums.ConfigFileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tracked_config_files")
public class TrackedConfigFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_label", nullable = false, length = 150)
    private String fileLabel;

    @Column(name = "check_interval_seconds", nullable = false)
    private Integer checkIntervalSeconds;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "current_hash", length = 64)
    private String currentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigFileStatus status;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "force_check_requested", nullable = false)
    private Boolean forceCheckRequested;

    @Column(name = "baseline_accepted_by", length = 100)
    private String baselineAcceptedBy;

    @Column(name = "baseline_accepted_at")
    private LocalDateTime baselineAcceptedAt;

    public TrackedConfigFile() {
    }

    public TrackedConfigFile(Server server, String filePath, String fileLabel, Integer checkIntervalSeconds, String createdBy) {
        this.server = server;
        this.filePath = filePath;
        this.fileLabel = fileLabel;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.status = ConfigFileStatus.PENDING;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.forceCheckRequested = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
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

    public ConfigFileStatus getStatus() {
        return status;
    }

    public void setStatus(ConfigFileStatus status) {
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
