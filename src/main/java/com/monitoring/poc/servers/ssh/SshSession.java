package com.monitoring.poc.servers.ssh;

/**
 * Thin abstraction over a single provisioning SSH session so
 * {@code SshProvisioningService} can be unit-tested against a mock without
 * touching real sockets or JSch's concrete classes.
 */
public interface SshSession {

    /**
     * Executes a bounded remote command and returns its trimmed stdout.
     * Implementations must throw if the remote exit status is non-zero.
     */
    String exec(String command);

    void uploadFile(String remotePath, byte[] content);

    void disconnect();
}
