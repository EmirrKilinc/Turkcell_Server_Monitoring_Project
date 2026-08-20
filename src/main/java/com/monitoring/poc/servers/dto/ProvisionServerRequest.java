package com.monitoring.poc.servers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class ProvisionServerRequest {

    @NotBlank
    private String ip;

    private String sshUsername;

    // char[], not String: lets the service layer wipe the password from
    // memory the moment it's done with it, instead of leaving an immutable
    // copy for the GC to eventually clean up.
    @NotEmpty
    private char[] sshPassword;

    public ProvisionServerRequest() {
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getSshUsername() {
        return sshUsername;
    }

    public void setSshUsername(String sshUsername) {
        this.sshUsername = sshUsername;
    }

    public char[] getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(char[] sshPassword) {
        this.sshPassword = sshPassword;
    }
}
