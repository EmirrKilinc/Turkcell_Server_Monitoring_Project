package com.monitoring.poc.metrics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AgentResultsRequest {

    @NotNull
    @Valid
    private List<MetricResultDto> periodicResults;

    @NotNull
    @Valid
    private List<AgentFetchResultDto> fetchResults;

    public AgentResultsRequest() {
    }

    public List<MetricResultDto> getPeriodicResults() {
        return periodicResults;
    }

    public void setPeriodicResults(List<MetricResultDto> periodicResults) {
        this.periodicResults = periodicResults;
    }

    public List<AgentFetchResultDto> getFetchResults() {
        return fetchResults;
    }

    public void setFetchResults(List<AgentFetchResultDto> fetchResults) {
        this.fetchResults = fetchResults;
    }
}
