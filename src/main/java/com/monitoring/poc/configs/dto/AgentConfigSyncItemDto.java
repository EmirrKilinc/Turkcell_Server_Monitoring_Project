package com.monitoring.poc.configs.dto;

public class AgentConfigSyncItemDto {

    private Long trackedFileId;
    private String filePath;
    private String currentHash;

    public AgentConfigSyncItemDto() {
    }

    public AgentConfigSyncItemDto(Long trackedFileId, String filePath, String currentHash) {
        this.trackedFileId = trackedFileId;
        this.filePath = filePath;
        this.currentHash = currentHash;
    }

    public Long getTrackedFileId() {
        return trackedFileId;
    }

    public void setTrackedFileId(Long trackedFileId) {
        this.trackedFileId = trackedFileId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }
}
