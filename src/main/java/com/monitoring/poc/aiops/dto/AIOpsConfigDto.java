package com.monitoring.poc.aiops.dto;

import com.monitoring.poc.entity.AiOpsConfig;
import jakarta.validation.constraints.Email;

import java.util.ArrayList;
import java.util.List;

/**
 * Request/response body for GET|POST /api/v1/aiops/config. {@code
 * trackedGroupIds} are real {@link com.monitoring.poc.entity.MetricGroup}
 * ids (see /api/v1/aiops/available-groups) - an empty list means the AIOps
 * module watches nothing and its scheduled jobs no-op (see AIOpsService).
 */
public class AIOpsConfigDto {

    private List<Long> trackedGroupIds = new ArrayList<>();

    private boolean alertEmailEnabled;

    @Email(message = "Gecerli bir e-posta adresi girin")
    private String alertEmail;

    private boolean dailySummaryEnabled;

    public AIOpsConfigDto() {
    }

    public static AIOpsConfigDto from(AiOpsConfig e) {
        AIOpsConfigDto dto = new AIOpsConfigDto();
        dto.trackedGroupIds = new ArrayList<>(e.getTrackedGroupIds());
        dto.alertEmailEnabled = Boolean.TRUE.equals(e.getAlertEmailEnabled());
        dto.alertEmail = e.getAlertEmail();
        dto.dailySummaryEnabled = Boolean.TRUE.equals(e.getDailySummaryEnabled());
        return dto;
    }

    public List<Long> getTrackedGroupIds() {
        return trackedGroupIds;
    }

    public void setTrackedGroupIds(List<Long> trackedGroupIds) {
        this.trackedGroupIds = trackedGroupIds;
    }

    public boolean isAlertEmailEnabled() {
        return alertEmailEnabled;
    }

    public void setAlertEmailEnabled(boolean alertEmailEnabled) {
        this.alertEmailEnabled = alertEmailEnabled;
    }

    public String getAlertEmail() {
        return alertEmail;
    }

    public void setAlertEmail(String alertEmail) {
        this.alertEmail = alertEmail;
    }

    public boolean isDailySummaryEnabled() {
        return dailySummaryEnabled;
    }

    public void setDailySummaryEnabled(boolean dailySummaryEnabled) {
        this.dailySummaryEnabled = dailySummaryEnabled;
    }
}
