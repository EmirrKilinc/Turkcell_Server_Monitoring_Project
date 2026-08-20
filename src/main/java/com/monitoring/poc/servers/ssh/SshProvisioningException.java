package com.monitoring.poc.servers.ssh;

public class SshProvisioningException extends RuntimeException {

    public SshProvisioningException(String message) {
        super(message);
    }

    public SshProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
