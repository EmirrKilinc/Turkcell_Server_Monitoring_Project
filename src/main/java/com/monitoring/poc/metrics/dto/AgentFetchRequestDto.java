package com.monitoring.poc.metrics.dto;

import java.util.List;

public class AgentFetchRequestDto {

    private Long fetchRequestId;
    private Long groupId;
    private List<AgentMetricItemDto> items;

    public AgentFetchRequestDto() {
    }

    public AgentFetchRequestDto(Long fetchRequestId, Long groupId, List<AgentMetricItemDto> items) {
        this.fetchRequestId = fetchRequestId;
        this.groupId = groupId;
        this.items = items;
    }

    public Long getFetchRequestId() {
        return fetchRequestId;
    }

    public void setFetchRequestId(Long fetchRequestId) {
        this.fetchRequestId = fetchRequestId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public List<AgentMetricItemDto> getItems() {
        return items;
    }

    public void setItems(List<AgentMetricItemDto> items) {
        this.items = items;
    }
}
