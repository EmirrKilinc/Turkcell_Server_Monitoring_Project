package com.monitoring.poc.metrics.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.poc.entity.MetricFetchRequest;

import java.time.LocalDateTime;

public class MetricFetchRequestResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private Long groupId;
    private Long serverId;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private Object resultPayload;

    public MetricFetchRequestResponse() {
    }

    public MetricFetchRequestResponse(MetricFetchRequest e) {
        this.id = e.getId();
        this.groupId = e.getGroupId();
        this.serverId = e.getServerId();
        this.status = e.getStatus().name();
        this.requestedAt = e.getRequestedAt();
        this.completedAt = e.getCompletedAt();
        this.resultPayload = parse(e.getResultPayload());
    }

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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Object getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(Object resultPayload) {
        this.resultPayload = resultPayload;
    }
}
