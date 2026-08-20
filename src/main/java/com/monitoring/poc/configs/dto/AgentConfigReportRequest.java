package com.monitoring.poc.configs.dto;

import jakarta.validation.constraints.NotNull;

/**
 * What the agent reports for one tracked file after a check. Exactly one of
 * the three outcomes applies: {@code error} set (FILE_NOT_FOUND /
 * PERMISSION_DENIED, no hash/content), {@code unchanged=true} (hash matched
 * what sync told it, no content sent), or {@code hash}+{@code content} set
 * (new/changed content) - the server, not the agent, decides whether that
 * lands as the first baseline (IN_SYNC) or a real drift (DRIFT_DETECTED).
 */
public class AgentConfigReportRequest {

    @NotNull
    private Long trackedFileId;

    private Boolean unchanged;

    private String hash;

    private String content;

    private String error;

    public AgentConfigReportRequest() {
    }

    public Long getTrackedFileId() {
        return trackedFileId;
    }

    public void setTrackedFileId(Long trackedFileId) {
        this.trackedFileId = trackedFileId;
    }

    public Boolean getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(Boolean unchanged) {
        this.unchanged = unchanged;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
