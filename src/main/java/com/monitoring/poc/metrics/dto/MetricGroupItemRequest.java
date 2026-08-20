package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.enums.ExecutionMode;
import jakarta.validation.constraints.NotNull;

public class MetricGroupItemRequest {

    @NotNull
    private Long metricDefinitionId;

    // Null means "inherit the group's default execution mode".
    private ExecutionMode overrideExecutionMode;

    public MetricGroupItemRequest() {
    }

    public Long getMetricDefinitionId() {
        return metricDefinitionId;
    }

    public void setMetricDefinitionId(Long metricDefinitionId) {
        this.metricDefinitionId = metricDefinitionId;
    }

    public ExecutionMode getOverrideExecutionMode() {
        return overrideExecutionMode;
    }

    public void setOverrideExecutionMode(ExecutionMode overrideExecutionMode) {
        this.overrideExecutionMode = overrideExecutionMode;
    }
}
