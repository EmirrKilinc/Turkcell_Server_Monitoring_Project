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
@Table(name = "config_file_history", indexes = {
        @Index(name = "idx_config_file_history_tracked", columnList = "trackedFileId, versionNumber")
})
public class ConfigFileHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracked_file_id", nullable = false)
    private Long trackedFileId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_content", nullable = false, columnDefinition = "TEXT")
    private String fileContent;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "diff_summary", length = 200)
    private String diffSummary;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    public ConfigFileHistory() {
    }

    public ConfigFileHistory(Long trackedFileId, Integer versionNumber, String fileContent,
                              String fileHash, String diffSummary) {
        this.trackedFileId = trackedFileId;
        this.versionNumber = versionNumber;
        this.fileContent = fileContent;
        this.fileHash = fileHash;
        this.diffSummary = diffSummary;
        this.capturedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTrackedFileId() {
        return trackedFileId;
    }

    public void setTrackedFileId(Long trackedFileId) {
        this.trackedFileId = trackedFileId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
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
