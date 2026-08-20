package com.monitoring.poc.metrics.dto;

import java.util.List;

public class AgentSyncResponse {

    private List<AgentMetricItemDto> dueItems;
    private List<AgentFetchRequestDto> pendingFetchRequests;

    public AgentSyncResponse() {
    }

    public AgentSyncResponse(List<AgentMetricItemDto> dueItems, List<AgentFetchRequestDto> pendingFetchRequests) {
        this.dueItems = dueItems;
        this.pendingFetchRequests = pendingFetchRequests;
    }

    public List<AgentMetricItemDto> getDueItems() {
        return dueItems;
    }

    public void setDueItems(List<AgentMetricItemDto> dueItems) {
        this.dueItems = dueItems;
    }

    public List<AgentFetchRequestDto> getPendingFetchRequests() {
        return pendingFetchRequests;
    }

    public void setPendingFetchRequests(List<AgentFetchRequestDto> pendingFetchRequests) {
        this.pendingFetchRequests = pendingFetchRequests;
    }
}
