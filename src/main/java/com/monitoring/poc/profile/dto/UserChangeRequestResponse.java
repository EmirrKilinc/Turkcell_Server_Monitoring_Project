package com.monitoring.poc.profile.dto;

import com.monitoring.poc.entity.UserChangeRequest;

import java.time.LocalDateTime;

public class UserChangeRequestResponse {

    private Long id;
    private String username;
    private String requestType;
    private String newEmail;
    private String status;
    private String reviewedBy;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public UserChangeRequestResponse() {
    }

    public UserChangeRequestResponse(UserChangeRequest r) {
        this.id = r.getId();
        this.username = r.getUser() != null ? r.getUser().getUsername() : null;
        this.requestType = r.getRequestType() != null ? r.getRequestType().name() : null;
        this.newEmail = r.getNewEmail();
        this.status = r.getStatus() != null ? r.getStatus().name() : null;
        this.reviewedBy = r.getReviewedBy();
        this.rejectionReason = r.getRejectionReason();
        this.createdAt = r.getCreatedAt();
        this.reviewedAt = r.getReviewedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
