package com.monitoring.poc.metrics.dto;

public class MetricApprovalRequest {

    private String rejectionReason;

    public MetricApprovalRequest() {
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
