package com.monitoring.poc.metrics.dto;

import com.monitoring.poc.enums.MetricValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class MetricDefinitionRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String metricKey;

    @NotBlank
    private String category;

    @NotBlank
    private String command;

    @NotNull
    @Positive
    private Integer timeoutSeconds;

    @NotNull
    private MetricValueType valueType;

    private String extractPattern;

    private String description;

    public MetricDefinitionRequest() {
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

    public MetricValueType getValueType() {
        return valueType;
    }

    public void setValueType(MetricValueType valueType) {
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
}
