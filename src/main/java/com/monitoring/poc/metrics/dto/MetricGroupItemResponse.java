package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.entity.MetricGroupItem;

import java.time.LocalDateTime;

public class MetricGroupItemResponse {

    private Long id;
    private Long groupId;
    private Long metricDefinitionId;
    private String metricDefinitionName;
    private String metricKey;
    private String overrideExecutionMode;
    private String effectiveExecutionMode;
    private LocalDateTime lastRunAt;

    public MetricGroupItemResponse() {
    }

    public MetricGroupItemResponse(MetricGroupItem e) {
        this.id = e.getId();
        this.groupId = e.getGroup().getId();
        this.metricDefinitionId = e.getMetricDefinition().getId();
        this.metricDefinitionName = e.getMetricDefinition().getName();
        this.metricKey = e.getMetricDefinition().getMetricKey();
        this.overrideExecutionMode = e.getOverrideExecutionMode() != null ? e.getOverrideExecutionMode().name() : null;
        this.effectiveExecutionMode = (e.getOverrideExecutionMode() != null
                ? e.getOverrideExecutionMode()
                : e.getGroup().getDefaultExecutionMode()).name();
        this.lastRunAt = e.getLastRunAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getMetricDefinitionId() {
        return metricDefinitionId;
    }

    public void setMetricDefinitionId(Long metricDefinitionId) {
        this.metricDefinitionId = metricDefinitionId;
    }

    public String getMetricDefinitionName() {
        return metricDefinitionName;
    }

    public void setMetricDefinitionName(String metricDefinitionName) {
        this.metricDefinitionName = metricDefinitionName;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getOverrideExecutionMode() {
        return overrideExecutionMode;
    }

    public void setOverrideExecutionMode(String overrideExecutionMode) {
        this.overrideExecutionMode = overrideExecutionMode;
    }

    public String getEffectiveExecutionMode() {
        return effectiveExecutionMode;
    }

    public void setEffectiveExecutionMode(String effectiveExecutionMode) {
        this.effectiveExecutionMode = effectiveExecutionMode;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
}
