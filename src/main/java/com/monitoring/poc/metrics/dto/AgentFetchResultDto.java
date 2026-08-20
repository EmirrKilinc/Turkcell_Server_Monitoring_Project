package com.monitoring.poc.metrics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AgentFetchResultDto {

    @NotNull
    private Long fetchRequestId;

    @Valid
    private List<MetricResultDto> results;

    public AgentFetchResultDto() {
    }

    public Long getFetchRequestId() {
        return fetchRequestId;
    }

    public void setFetchRequestId(Long fetchRequestId) {
        this.fetchRequestId = fetchRequestId;
    }

    public List<MetricResultDto> getResults() {
        return results;
    }

    public void setResults(List<MetricResultDto> results) {
        this.results = results;
    }
}
