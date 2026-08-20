package com.monitoring.poc.configs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AgentConfigReportBatchRequest {

    @NotNull
    @Valid
    private List<AgentConfigReportRequest> reports;

    public AgentConfigReportBatchRequest() {
    }

    public List<AgentConfigReportRequest> getReports() {
        return reports;
    }

    public void setReports(List<AgentConfigReportRequest> reports) {
        this.reports = reports;
    }
}
