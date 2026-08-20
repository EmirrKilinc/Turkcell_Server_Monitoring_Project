package com.monitoring.poc.servers.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Real SSH implementation (com.github.mwiede:jsch, a maintained JSch fork
 * that keeps the classic com.jcraft.jsch package/API surface). Only used for
 * zero-persistence provisioning - the password lives in this call's stack
 * frame only, and our own byte[]/char[] copies are wiped the instant
 * {@code session.setPassword} has taken its own internal copy (JSch clones
 * the array it's given). The 15s connect timeout is a strict upper bound so a
 * dead/unreachable target host never leaves a provisioning request hanging.
 */
@Component
public class JSchClientFactoryImpl implements JSchClientFactory {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    // Separate, tighter bound than CONNECT_TIMEOUT_MS: caps how long we'll
    // wait for a single already-connected exec channel to finish and report
    // its exit status. Without this the "while (!channel.isClosed())" poll
    // loop below has no upper bound at all and can spin forever if a remote
    // command (or a misbehaving background process still holding the
    // channel's stdio open) never signals completion.
    private static final int EXEC_TIMEOUT_MS = 10000;
    private static final int SSH_PORT = 22;

    @Override
    public SshSession connect(String host, String username, char[] password) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, SSH_PORT);

            byte[] passwordBytes = toUtf8Bytes(password);
            try {
                session.setPassword(passwordBytes);
            } finally {
                Arrays.fill(passwordBytes, (byte) 0);
            }

            // PoC-grade: skip host key verification. Harden with a provisioned
            // known_hosts file before using this against untrusted networks.
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT_MS);
            return new JSchSshSession(session);
        } catch (Exception e) {
            throw new SshProvisioningException("SSH baglantisi kurulamadi: " + host, e);
        }
    }

    private byte[] toUtf8Bytes(char[] chars) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    private static final class JSchSshSession implements SshSession {
        private final Session session;

        JSchSshSession(Session session) {
            this.session = session;
        }

        @Override
        public String exec(String command) {
            ChannelExec channel = null;
            try {
                channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                channel.setOutputStream(stdout);
                channel.setErrStream(stderr);
                channel.connect(CONNECT_TIMEOUT_MS);

                long deadline = System.currentTimeMillis() + EXEC_TIMEOUT_MS;
                while (!channel.isClosed()) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw new SshProvisioningException("Komut " + (EXEC_TIMEOUT_MS / 1000)
                                + " saniye icinde tamamlanamadi (zaman asimi): " + command);
                    }
                    Thread.sleep(50);
                }

                int exitStatus = channel.getExitStatus();
                if (exitStatus != 0) {
                    throw new SshProvisioningException(
                            "Komut basarisiz (exit " + exitStatus + "): " + command + " -> " + stderr);
                }
                return stdout.toString(StandardCharsets.UTF_8).trim();
            } catch (SshProvisioningException e) {
                throw e;
            } catch (Exception e) {
                throw new SshProvisioningException("Komut calistirilamadi: " + command, e);
            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
            }
        }

        @Override
        public void uploadFile(String remotePath, byte[] content) {
            ChannelSftp channel = null;
            try {
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(CONNECT_TIMEOUT_MS);
                try (InputStream in = new ByteArrayInputStream(content)) {
                    channel.put(in, remotePath);
                }
            } catch (Exception e) {
                throw new SshProvisioningException("Dosya yuklenemedi: " + remotePath, e);
            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
            }
        }

        @Override
        public void disconnect() {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
