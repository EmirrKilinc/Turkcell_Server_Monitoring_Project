package com.monitoring.poc.metrics.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.poc.entity.CustomMetricLog;

import java.time.LocalDateTime;

public class CustomMetricLogResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private Long serverId;
    private Long groupId;
    private Long groupItemId;
    private String metricKey;
    private Boolean success;
    private Object resultPayload;
    private LocalDateTime createdAt;

    public CustomMetricLogResponse() {
    }

    public CustomMetricLogResponse(CustomMetricLog e) {
        this.id = e.getId();
        this.serverId = e.getServerId();
        this.groupId = e.getGroupId();
        this.groupItemId = e.getGroupItemId();
        this.metricKey = e.getMetricKey();
        this.success = e.getSuccess();
        this.resultPayload = parse(e.getResultPayload());
        this.createdAt = e.getCreatedAt();
    }

    // Defensive: a hand-edited or historically malformed row must never turn
    // into a 500 when listing logs - fall back to the raw text.
    private static Object parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
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

    public Object getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(Object resultPayload) {
        this.resultPayload = resultPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
