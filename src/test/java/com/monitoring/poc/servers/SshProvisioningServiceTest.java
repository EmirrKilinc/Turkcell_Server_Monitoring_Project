package com.monitoring.poc.servers;

import com.monitoring.poc.entity.ServerProvisioningScript;
import com.monitoring.poc.repository.ServerProvisioningScriptRepository;
import com.monitoring.poc.servers.ssh.JSchClientFactory;
import com.monitoring.poc.servers.ssh.SshProvisioningException;
import com.monitoring.poc.servers.ssh.SshSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SshProvisioningServiceTest {

    private JSchClientFactory clientFactory;
    private SshSession session;
    private ServerProvisioningScriptRepository provisioningScriptRepository;
    private SshProvisioningService service;

    // The service wipes the password array right after use, so we can't
    // assert its content post-hoc via a plain Mockito verify() (which
    // compares the live array at verification time). Instead the stubbed
    // connect() snapshots whatever it was called with the instant it's
    // invoked, before any wiping happens.
    private String capturedPassword;

    @BeforeEach
    void setUp() {
        clientFactory = mock(JSchClientFactory.class);
        session = mock(SshSession.class);
        provisioningScriptRepository = mock(ServerProvisioningScriptRepository.class);
        when(provisioningScriptRepository.findByIsEnabledTrueOrderByExecutionOrderAscIdAsc())
                .thenReturn(List.of());
        when(clientFactory.connect(anyString(), anyString(), any(char[].class))).thenAnswer(invocation -> {
            capturedPassword = new String((char[]) invocation.getArgument(2));
            return session;
        });
        service = new SshProvisioningService(clientFactory, provisioningScriptRepository, "http://10.0.0.1:8080", "/data01");
    }

    @Test
    void provisionRunsExpectedCommandSequenceAndUploadsAgentFiles() {
        when(session.exec("hostname")).thenReturn("web-01");

        ProvisioningResult result = service.provision("10.0.0.5", "root", "s3cret-pass".toCharArray());

        assertThat(result.hostname()).isEqualTo("web-01");
        assertThat(result.rawSecretKey()).isNotBlank();

        verify(clientFactory).connect(eq("10.0.0.5"), eq("root"), any(char[].class));
        assertThat(capturedPassword).isEqualTo("s3cret-pass");

        verify(session).exec("hostname");
        verify(session).exec("NOLOGIN_SHELL=$(command -v nologin || echo /bin/false); "
                + "if id monitoring_user >/dev/null 2>&1; then usermod -s \"$NOLOGIN_SHELL\" monitoring_user; "
                + "else useradd -m -s \"$NOLOGIN_SHELL\" monitoring_user; fi");
        verify(session).exec("pkill -f '/data01/monitoring/[a]gent.py' >/dev/null 2>&1 || true");
        verify(session).exec("rm -rf /data01/monitoring");
        verify(session).exec("mkdir -p /data01/monitoring");
        verify(session).exec("chown -R monitoring_user:monitoring_user /data01/monitoring");
        verify(session).exec("chmod 700 /data01/monitoring");
        verify(session).exec("chown -R monitoring_user:monitoring_user /data01/monitoring/agent.py /data01/monitoring/agent_config.json");
        verify(session).exec("chmod 600 /data01/monitoring/agent_config.json");
        verify(session).exec("su -s /bin/sh monitoring_user -c \"nohup python3 /data01/monitoring/agent.py > "
                + "/data01/monitoring/agent.log 2>&1 < /dev/null &\"");
        verify(session).exec("(crontab -u monitoring_user -l 2>/dev/null | grep -v 'agent.py'; "
                + "echo '@reboot cd /data01/monitoring && nohup python3 agent.py > "
                + "/data01/monitoring/agent.log 2>&1 < /dev/null &') | crontab -u monitoring_user -");

        // Root is only ever used to open the SSH session itself - no command
        // run over it may use sudo or touch systemd.
        verify(session, never()).exec(org.mockito.ArgumentMatchers.contains("sudo"));
        verify(session, never()).exec(org.mockito.ArgumentMatchers.contains("systemctl"));
        verify(session, never()).exec(org.mockito.ArgumentMatchers.contains("/etc/systemd"));

        verify(session, times(2)).uploadFile(anyString(), any(byte[].class));
        verify(session).uploadFile(eq("/data01/monitoring/agent.py"), any(byte[].class));
        verify(session).uploadFile(eq("/data01/monitoring/agent_config.json"), any(byte[].class));

        verify(session).disconnect();
    }

    @Test
    void stopCommandUsesASelfImmunePkillPatternAndCleansUpBeforeRebuilding() {
        when(session.exec("hostname")).thenReturn("web-05");

        service.provision("10.0.0.11", "root", "pw".toCharArray());

        // Regression guard for the "exit -1" bug: a plain (non-bracketed)
        // pkill -f pattern matches pkill's own argv and kills itself before
        // it can report an exit status. Must never regress to this form,
        // even though it's exactly the form later suggested (from a stale
        // repro) as a "fix" - reintroducing it would bring the bug back.
        verify(session, never()).exec("pkill -f '/data01/monitoring/agent.py' || true");
        verify(session, never()).exec("pkill -f '/data01/monitoring/agent.py' >/dev/null 2>&1 || true");
        verify(session).exec("pkill -f '/data01/monitoring/[a]gent.py' >/dev/null 2>&1 || true");

        InOrder order = inOrder(session);
        order.verify(session).exec("pkill -f '/data01/monitoring/[a]gent.py' >/dev/null 2>&1 || true");
        order.verify(session).exec("rm -rf /data01/monitoring");
        order.verify(session).exec("mkdir -p /data01/monitoring");
        order.verify(session).exec("su -s /bin/sh monitoring_user -c \"nohup python3 /data01/monitoring/agent.py > "
                + "/data01/monitoring/agent.log 2>&1 < /dev/null &\"");
    }

    @Test
    void generatedConfigContainsSecretIngestUrlAndHostname() {
        when(session.exec("hostname")).thenReturn("db-02");

        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        service.provision("10.0.0.6", "root", "pw".toCharArray());

        verify(session).uploadFile(eq("/data01/monitoring/agent_config.json"), contentCaptor.capture());
        String configJson = new String(contentCaptor.getValue());

        assertThat(configJson).contains("\"hostname\": \"db-02\"");
        assertThat(configJson).contains("\"ingestUrl\": \"http://10.0.0.1:8080/api/metrics\"");
        assertThat(configJson).contains("\"syncUrl\": \"http://10.0.0.1:8080/api/agent/metrics/sync\"");
        assertThat(configJson).contains("\"resultsUrl\": \"http://10.0.0.1:8080/api/agent/metrics/results\"");
    }

    @Test
    void disconnectsSessionEvenWhenACommandFails() {
        when(session.exec("hostname")).thenThrow(new SshProvisioningException("boom"));

        assertThatThrownBy(() -> service.provision("10.0.0.7", "root", "pw".toCharArray()))
                .isInstanceOf(SshProvisioningException.class);

        verify(session).disconnect();
        verify(session, never()).uploadFile(anyString(), any(byte[].class));
    }

    @Test
    void neverLogsOrEchoesThePasswordAnywhereInTheResult() {
        when(session.exec("hostname")).thenReturn("web-03");

        ProvisioningResult result = service.provision("10.0.0.8", "root", "top-secret-password".toCharArray());

        assertThat(result.toString()).doesNotContain("top-secret-password");
        assertThat(result.hostname()).doesNotContain("top-secret-password");
        assertThat(result.rawSecretKey()).doesNotContain("top-secret-password");
    }

    @Test
    void wipesThePasswordArrayAfterProvisioning() {
        when(session.exec("hostname")).thenReturn("web-04");
        char[] password = "wipe-me-please".toCharArray();

        service.provision("10.0.0.9", "root", password);

        assertThat(password).containsOnly('\0');
    }

    @Test
    void wipesThePasswordArrayEvenWhenProvisioningFails() {
        when(session.exec("hostname")).thenThrow(new SshProvisioningException("boom"));
        char[] password = "wipe-me-too".toCharArray();

        assertThatThrownBy(() -> service.provision("10.0.0.10", "root", password))
                .isInstanceOf(SshProvisioningException.class);

        assertThat(password).containsOnly('\0');
    }

    @Test
    void runsEnabledProvisioningScriptsInOrderBeforeTheHardenedSteps() {
        when(session.exec("hostname")).thenReturn("web-06");
        ServerProvisioningScript first = scriptWith(1L, "useradd -m -s /bin/bash monitoring_user || true", 1);
        ServerProvisioningScript second = scriptWith(2L, "setfacl -m u:monitoring_user:r /etc/ssh/sshd_config", 2);
        when(provisioningScriptRepository.findByIsEnabledTrueOrderByExecutionOrderAscIdAsc())
                .thenReturn(List.of(first, second));

        service.provision("10.0.0.12", "root", "pw".toCharArray());

        InOrder order = inOrder(session);
        order.verify(session).exec("hostname");
        order.verify(session).exec("useradd -m -s /bin/bash monitoring_user || true");
        order.verify(session).exec("setfacl -m u:monitoring_user:r /etc/ssh/sshd_config");
        order.verify(session).exec("NOLOGIN_SHELL=$(command -v nologin || echo /bin/false); "
                + "if id monitoring_user >/dev/null 2>&1; then usermod -s \"$NOLOGIN_SHELL\" monitoring_user; "
                + "else useradd -m -s \"$NOLOGIN_SHELL\" monitoring_user; fi");
    }

    @Test
    void skipsDisabledProvisioningScripts() {
        when(session.exec("hostname")).thenReturn("web-07");
        when(provisioningScriptRepository.findByIsEnabledTrueOrderByExecutionOrderAscIdAsc())
                .thenReturn(List.of());

        service.provision("10.0.0.13", "root", "pw".toCharArray());

        verify(session, never()).exec("rm -rf /");
    }

    @Test
    void aFailingProvisioningScriptAbortsBeforeTheHardenedStepsRun() {
        when(session.exec("hostname")).thenReturn("web-08");
        ServerProvisioningScript failing = scriptWith(3L, "false", 1);
        when(provisioningScriptRepository.findByIsEnabledTrueOrderByExecutionOrderAscIdAsc())
                .thenReturn(List.of(failing));
        when(session.exec("false")).thenThrow(new SshProvisioningException("exit 1"));

        assertThatThrownBy(() -> service.provision("10.0.0.14", "root", "pw".toCharArray()))
                .isInstanceOf(SshProvisioningException.class)
                .hasMessageContaining("false");

        verify(session, never()).exec("NOLOGIN_SHELL=$(command -v nologin || echo /bin/false); "
                + "if id monitoring_user >/dev/null 2>&1; then usermod -s \"$NOLOGIN_SHELL\" monitoring_user; "
                + "else useradd -m -s \"$NOLOGIN_SHELL\" monitoring_user; fi");
        verify(session).disconnect();
    }

    private ServerProvisioningScript scriptWith(Long id, String commandLine, int order) {
        ServerProvisioningScript script = new ServerProvisioningScript(commandLine, "test-script-" + id, order);
        script.setId(id);
        return script;
    }
}
