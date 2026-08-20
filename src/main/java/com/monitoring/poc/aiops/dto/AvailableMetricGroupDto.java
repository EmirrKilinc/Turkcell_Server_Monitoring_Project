package com.monitoring.poc.aiops.dto;

import com.monitoring.poc.entity.MetricGroup;

/**
 * Response shape for GET /api/v1/aiops/available-groups: every real metric
 * group in the system (across all servers), for the "Izlenecek Metrik
 * Gruplari" picker and the chat scope selector on the frontend.
 */
public class AvailableMetricGroupDto {

    private Long id;
    private String name;
    private Long serverId;
    private String serverHostname;

    public AvailableMetricGroupDto() {
    }

    public static AvailableMetricGroupDto from(MetricGroup e) {
        AvailableMetricGroupDto dto = new AvailableMetricGroupDto();
        dto.id = e.getId();
        dto.name = e.getName();
        dto.serverId = e.getServer().getId();
        dto.serverHostname = e.getServer().getHostname();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getServerHostname() {
        return serverHostname;
    }

    public void setServerHostname(String serverHostname) {
        this.serverHostname = serverHostname;
    }
}
