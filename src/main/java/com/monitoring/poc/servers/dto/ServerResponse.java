package com.monitoring.poc.servers.dto;

import com.monitoring.poc.entity.Server;

import java.time.LocalDateTime;

public class ServerResponse {

    private Long id;
    private String hostname;
    private String ipAddress;
    private String status;
    private String addedByUsername;
    private LocalDateTime createdAt;

    public ServerResponse() {
    }

    public ServerResponse(Server server) {
        this.id = server.getId();
        this.hostname = server.getHostname();
        this.ipAddress = server.getIpAddress();
        this.status = server.getStatus().name();
        this.addedByUsername = server.getAddedBy() != null ? server.getAddedBy().getUsername() : null;
        this.createdAt = server.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddedByUsername() {
        return addedByUsername;
    }

    public void setAddedByUsername(String addedByUsername) {
        this.addedByUsername = addedByUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
