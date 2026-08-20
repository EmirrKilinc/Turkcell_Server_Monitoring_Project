package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.entity.MetricDefinition;

import java.time.LocalDateTime;

public class MetricDefinitionResponse {

    private Long id;
    private String name;
    private String metricKey;
    private String category;
    private String command;
    private Integer timeoutSeconds;
    private String valueType;
    private String extractPattern;
    private String description;
    private String approvalStatus;
    private String createdBy;
    private String approvedBy;
    private String rejectionReason;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MetricDefinitionResponse() {
    }

    public MetricDefinitionResponse(MetricDefinition e) {
        this.id = e.getId();
        this.name = e.getName();
        this.metricKey = e.getMetricKey();
        this.category = e.getCategory();
        this.command = e.getCommand();
        this.timeoutSeconds = e.getTimeoutSeconds();
        this.valueType = e.getValueType() != null ? e.getValueType().name() : null;
        this.extractPattern = e.getExtractPattern();
        this.description = e.getDescription();
        this.approvalStatus = e.getApprovalStatus() != null ? e.getApprovalStatus().name() : null;
        this.createdBy = e.getCreatedBy();
        this.approvedBy = e.getApprovedBy();
        this.rejectionReason = e.getRejectionReason();
        this.approvedAt = e.getApprovedAt();
        this.createdAt = e.getCreatedAt();
        this.updatedAt = e.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getExtractPattern() {
        return extractPattern;
    }

    public void setExtractPattern(String extractPattern) {
        this.extractPattern = extractPattern;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
