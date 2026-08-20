package com.monitoring.poc.configs.dto;

import com.monitoring.poc.entity.ConfigFileHistory;

import java.time.LocalDateTime;

public class ConfigFileHistorySummaryResponse {

    private Integer versionNumber;
    private String fileHash;
    private String diffSummary;
    private LocalDateTime capturedAt;

    public ConfigFileHistorySummaryResponse() {
    }

    public ConfigFileHistorySummaryResponse(ConfigFileHistory e) {
        this.versionNumber = e.getVersionNumber();
        this.fileHash = e.getFileHash();
        this.diffSummary = e.getDiffSummary();
        this.capturedAt = e.getCapturedAt();
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getDiffSummary() {
        return diffSummary;
    }

    public void setDiffSummary(String diffSummary) {
        this.diffSummary = diffSummary;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }
}
