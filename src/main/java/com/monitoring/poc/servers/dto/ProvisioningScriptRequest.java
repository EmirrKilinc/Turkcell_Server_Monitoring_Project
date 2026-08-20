package com.monitoring.poc.servers.dto;

import jakarta.validation.constraints.NotBlank;

public class ProvisioningScriptRequest {

    @NotBlank
    private String commandLine;

    @NotBlank
    private String description;

    public ProvisioningScriptRequest() {
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
