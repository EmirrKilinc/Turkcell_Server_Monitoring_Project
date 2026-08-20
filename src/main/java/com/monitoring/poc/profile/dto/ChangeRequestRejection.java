package com.monitoring.poc.profile.dto;

public class ChangeRequestRejection {

    private String rejectionReason;

    public ChangeRequestRejection() {
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
