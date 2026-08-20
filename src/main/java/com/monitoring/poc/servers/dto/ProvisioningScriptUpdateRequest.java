package com.monitoring.poc.servers.dto;

/**
 * Partial update - only non-null fields are applied by the service, so a
 * caller flipping just the enabled switch doesn't need to resend the
 * command/description.
 */
public class ProvisioningScriptUpdateRequest {

    private String commandLine;

    private String description;

    private Boolean isEnabled;

    public ProvisioningScriptUpdateRequest() {
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

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
