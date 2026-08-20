package com.monitoring.poc.aiops.dto;

import com.monitoring.poc.entity.AiOpsAnomaly;

import java.time.LocalDateTime;

/**
 * Response shape for GET /api/v1/aiops/anomalies. {@code severity} is
 * deliberately lower-cased ("info"/"warning"/"critical") to match the
 * frontend's CSS class / SEVERITY_META keys directly (see assets/aiops.js).
 */
public class AnomalyCardDto {

    private String id;
    private String title;
    private String severity;
    private String content;
    private LocalDateTime timestamp;

    public AnomalyCardDto() {
    }

    public static AnomalyCardDto from(AiOpsAnomaly e) {
        AnomalyCardDto dto = new AnomalyCardDto();
        dto.id = String.valueOf(e.getId());
        dto.title = e.getTitle();
        dto.severity = e.getSeverity().name().toLowerCase();
        dto.content = e.getContent();
        dto.timestamp = e.getCreatedAt();
        return dto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
