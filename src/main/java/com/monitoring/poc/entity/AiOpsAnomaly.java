package com.monitoring.poc.entity;

import com.monitoring.poc.enums.AiOpsAnomalyCategory;
import com.monitoring.poc.enums.AiOpsSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A single AI-generated anomaly / RCA / daily-summary card, produced by
 * {@code AIOpsService} (either from a live chat turn or from
 * {@code AIOpsScheduler}'s periodic scans) and served back out through
 * GET /api/v1/aiops/anomalies.
 */
@Entity
@Table(name = "aiops_anomalies", indexes = {
        @Index(name = "idx_aiops_anomalies_created_at", columnList = "createdAt")
})
public class AiOpsAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiOpsAnomalyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiOpsSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AiOpsAnomaly() {
    }

    public AiOpsAnomaly(AiOpsAnomalyCategory category, AiOpsSeverity severity, String title,
                         String content, LocalDateTime createdAt) {
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AiOpsAnomalyCategory getCategory() {
        return category;
    }

    public void setCategory(AiOpsAnomalyCategory category) {
        this.category = category;
    }

    public AiOpsSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AiOpsSeverity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
