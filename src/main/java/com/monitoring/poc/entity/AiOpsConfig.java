package com.monitoring.poc.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Single-row (singleton, id always {@link #SINGLETON_ID}) settings record for
 * the AIOps module - which real {@link MetricGroup} ids the local LLM
 * watches, and where/whether to email alerts. Seeded once by the V15 Flyway
 * migration, re-shaped from category switches to real group ids by V16.
 */
@Entity
@Table(name = "aiops_config")
public class AiOpsConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "aiops_tracked_groups", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "group_id")
    private Set<Long> trackedGroupIds = new LinkedHashSet<>();

    @Column(name = "alert_email_enabled", nullable = false)
    private Boolean alertEmailEnabled;

    @Column(name = "alert_email", length = 200)
    private String alertEmail;

    @Column(name = "daily_summary_enabled", nullable = false)
    private Boolean dailySummaryEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiOpsConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Long> getTrackedGroupIds() {
        return trackedGroupIds;
    }

    public void setTrackedGroupIds(Set<Long> trackedGroupIds) {
        this.trackedGroupIds = trackedGroupIds;
    }

    public Boolean getAlertEmailEnabled() {
        return alertEmailEnabled;
    }

    public void setAlertEmailEnabled(Boolean alertEmailEnabled) {
        this.alertEmailEnabled = alertEmailEnabled;
    }

    public String getAlertEmail() {
        return alertEmail;
    }

    public void setAlertEmail(String alertEmail) {
        this.alertEmail = alertEmail;
    }

    public Boolean getDailySummaryEnabled() {
        return dailySummaryEnabled;
    }

    public void setDailySummaryEnabled(Boolean dailySummaryEnabled) {
        this.dailySummaryEnabled = dailySummaryEnabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
