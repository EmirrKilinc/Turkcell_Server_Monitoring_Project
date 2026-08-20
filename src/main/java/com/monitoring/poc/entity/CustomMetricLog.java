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
@Table(name = "custom_metric_logs", indexes = {
        @Index(name = "idx_custom_metric_logs_server", columnList = "serverId, createdAt"),
        @Index(name = "idx_custom_metric_logs_group", columnList = "groupId, createdAt")
})
public class CustomMetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "group_item_id")
    private Long groupItemId;

    @Column(name = "metric_key", nullable = false, length = 100)
    private String metricKey;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "result_payload", nullable = false, columnDefinition = "TEXT")
    private String resultPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CustomMetricLog() {
    }

    public CustomMetricLog(Long serverId, Long groupId, Long groupItemId, String metricKey,
                            Boolean success, String resultPayload, LocalDateTime createdAt) {
        this.serverId = serverId;
        this.groupId = groupId;
        this.groupItemId = groupItemId;
        this.metricKey = metricKey;
        this.success = success;
        this.resultPayload = resultPayload;
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getGroupItemId() {
        return groupItemId;
    }

    public void setGroupItemId(Long groupItemId) {
        this.groupItemId = groupItemId;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload) {
        this.resultPayload = resultPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
