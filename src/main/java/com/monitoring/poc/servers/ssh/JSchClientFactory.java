package com.monitoring.poc.servers.ssh;

/**
 * Opens the SSH session used for zero-persistence provisioning. The password
 * is used only for the duration of this call - implementations must not log
 * or persist it anywhere. char[] (not String) so the caller can wipe it from
 * memory the moment authentication is done, instead of waiting on GC to
 * reclaim an immutable String.
 */
public interface JSchClientFactory {

    SshSession connect(String host, String username, char[] password);
}
