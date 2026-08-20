package com.monitoring.poc.metrics.dto;

public class AgentMetricItemDto {

    private Long groupItemId;
    private Long groupId;
    private Long metricDefinitionId;
    private String metricKey;
    private String type;
    private String commandPayload;

    public AgentMetricItemDto() {
    }

    public AgentMetricItemDto(Long groupItemId, Long groupId, Long metricDefinitionId, String metricKey,
                               String type, String commandPayload) {
        this.groupItemId = groupItemId;
        this.groupId = groupId;
        this.metricDefinitionId = metricDefinitionId;
        this.metricKey = metricKey;
        this.type = type;
        this.commandPayload = commandPayload;
    }

    public Long getGroupItemId() {
        return groupItemId;
    }

    public void setGroupItemId(Long groupItemId) {
        this.groupItemId = groupItemId;
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

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCommandPayload() {
        return commandPayload;
    }

    public void setCommandPayload(String commandPayload) {
        this.commandPayload = commandPayload;
    }
}
