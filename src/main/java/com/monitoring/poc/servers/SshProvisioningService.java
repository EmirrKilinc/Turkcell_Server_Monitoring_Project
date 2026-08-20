package com.monitoring.poc.servers;

import com.monitoring.poc.entity.ServerProvisioningScript;
import com.monitoring.poc.repository.ServerProvisioningScriptRepository;
import com.monitoring.poc.servers.ssh.JSchClientFactory;
import com.monitoring.poc.servers.ssh.SshProvisioningException;
import com.monitoring.poc.servers.ssh.SshSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * One-time, zero-persistence root SSH provisioning. The caller supplies root
 * credentials for this single request only - they are used in-memory to (1)
 * create/harden the restricted {@code monitoring_user} account and (2) drop
 * the agent files into place, then discarded. Root is never persisted, never
 * logged, and never used again after this call returns: the agent itself
 * always runs as the unprivileged, shell-less monitoring_user, started via
 * {@code su} from this same root session and kept alive across reboots by a
 * per-user crontab {@code @reboot} entry (no systemd, since that would need
 * root at agent-runtime too).
 */
@Service
public class SshProvisioningService {

    private static final String AGENT_TEMPLATE_RESOURCE = "agent-template/agent.py";
    private static final String MONITORING_USER = "monitoring_user";

    private final JSchClientFactory clientFactory;
    private final ServerProvisioningScriptRepository provisioningScriptRepository;
    private final String ingestBaseUrl;
    private final String storageRoot;

    public SshProvisioningService(JSchClientFactory clientFactory,
                                   ServerProvisioningScriptRepository provisioningScriptRepository,
                                   @Value("${app.ingest-base-url}") String ingestBaseUrl,
                                   @Value("${app.storage.root}") String storageRoot) {
        this.clientFactory = clientFactory;
        this.provisioningScriptRepository = provisioningScriptRepository;
        this.ingestBaseUrl = ingestBaseUrl;
        this.storageRoot = storageRoot;
    }

    public ProvisioningResult provision(String ip, String sshUsername, char[] sshPassword) {
        String remoteDir = storageRoot + "/monitoring";
        SshSession session = null;

        try {
            session = clientFactory.connect(ip, sshUsername, sshPassword);

            String hostname = session.exec("hostname");
            if (hostname == null || hostname.isBlank()) {
                throw new SshProvisioningException("Hedef sunucudan hostname alinamadi");
            }

            // Admin-managed bootstrap hooks (e.g. extra setfacl grants) run
            // first, in execution_order. They run BEFORE the hardened steps
            // below on purpose: those steps unconditionally re-pin
            // monitoring_user's shell to nologin/false and reset directory
            // ownership regardless of what a script here did, so this
            // configurable list can never weaken the baseline security
            // invariants documented on this class.
            runProvisioningScripts(session);

            // Idempotent: creates monitoring_user if missing, and (re)pins its
            // shell to nologin/false either way, so a pre-existing account from
            // an older bootstrap run can never keep an interactive shell.
            session.exec(buildEnsureRestrictedUserCommand());

            // "Sunucu Ekle" always starts from a clean slate: kill any agent
            // process left running from a previous provisioning of this host,
            // wipe its old files/log, then rebuild everything from scratch
            // below. Without this, re-provisioning would leave stale files
            // and (briefly) two agents racing with two different secrets.
            session.exec(buildStopCommand(remoteDir));
            session.exec(buildResetDirectoryCommand(remoteDir));

            session.exec("mkdir -p " + remoteDir);
            session.exec(buildChownCommand(remoteDir));
            session.exec("chmod 700 " + remoteDir);

            String secretKey = generateSecretKey();
            String ingestUrl = ingestBaseUrl + "/api/metrics";
            String syncUrl = ingestBaseUrl + "/api/agent/metrics/sync";
            String resultsUrl = ingestBaseUrl + "/api/agent/metrics/results";
            String configSyncUrl = ingestBaseUrl + "/api/agent/configs/sync";
            String configReportUrl = ingestBaseUrl + "/api/agent/configs/report";

            session.uploadFile(remoteDir + "/agent.py", loadAgentTemplate());
            session.uploadFile(remoteDir + "/agent_config.json",
                    buildAgentConfigJson(secretKey, ingestUrl, syncUrl, resultsUrl, configSyncUrl, configReportUrl, hostname)
                            .getBytes(StandardCharsets.UTF_8));

            // Uploaded over the root session, so they land owned by root -
            // hand them back to monitoring_user and lock the config down since
            // it carries the raw HMAC secret.
            session.exec(buildChownCommand(remoteDir + "/agent.py " + remoteDir + "/agent_config.json"));
            session.exec("chmod 600 " + remoteDir + "/agent_config.json");

            session.exec(buildStartCommand(remoteDir));
            session.exec(buildCronCommand(remoteDir));

            return new ProvisioningResult(hostname, secretKey);
        } catch (SshProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new SshProvisioningException("Provisioning basarisiz oldu", e);
        } finally {
            if (sshPassword != null) {
                Arrays.fill(sshPassword, '\0');
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private void runProvisioningScripts(SshSession session) {
        for (ServerProvisioningScript script : provisioningScriptRepository.findByIsEnabledTrueOrderByExecutionOrderAscIdAsc()) {
            try {
                session.exec(script.getCommandLine());
            } catch (Exception e) {
                throw new SshProvisioningException(
                        "Provisioning script basarisiz oldu (\"" + script.getDescription() + "\"): " + script.getCommandLine(), e);
            }
        }
    }

    private String generateSecretKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] loadAgentTemplate() {
        try {
            return new ClassPathResource(AGENT_TEMPLATE_RESOURCE).getContentAsByteArray();
        } catch (IOException e) {
            throw new SshProvisioningException("Agent sablonu okunamadi", e);
        }
    }

    private String buildAgentConfigJson(String secretKey, String ingestUrl, String syncUrl, String resultsUrl,
                                         String configSyncUrl, String configReportUrl, String hostname) {
        return """
                {
                  "secretKey": "%s",
                  "ingestUrl": "%s",
                  "syncUrl": "%s",
                  "resultsUrl": "%s",
                  "configSyncUrl": "%s",
                  "configReportUrl": "%s",
                  "hostname": "%s"
                }
                """.formatted(secretKey, ingestUrl, syncUrl, resultsUrl, configSyncUrl, configReportUrl, hostname);
    }

    /**
     * Idempotent: creates monitoring_user with a login shell that can never
     * open an interactive session if it doesn't exist yet, and re-pins the
     * shell to the same restricted value if it already does (covers hosts
     * bootstrapped by an older, less strict version of this flow). Falls
     * back to /bin/false when /sbin/nologin isn't installed.
     */
    private String buildEnsureRestrictedUserCommand() {
        return "NOLOGIN_SHELL=$(command -v nologin || echo /bin/false); "
                + "if id " + MONITORING_USER + " >/dev/null 2>&1; then "
                + "usermod -s \"$NOLOGIN_SHELL\" " + MONITORING_USER + "; "
                + "else "
                + "useradd -m -s \"$NOLOGIN_SHELL\" " + MONITORING_USER + "; "
                + "fi";
    }

    private String buildChownCommand(String target) {
        return "chown -R " + MONITORING_USER + ":" + MONITORING_USER + " " + target;
    }

    /**
     * "|| true" is required: our SshSession.exec() throws on a non-zero exit
     * status, and pkill exits non-zero when there is nothing to kill (the
     * common case on first provisioning). Run directly as root, which can
     * signal any user's process without needing su.
     *
     * The pattern brackets the first letter of "agent.py" ("[a]gent.py").
     * pkill -f matches against the FULL command line of every process,
     * including its own - and pkill's own argv literally contains the
     * search pattern text. Without this trick the plain pattern always
     * self-matches (pkill sees its own "pkill -f .../agent.py" invocation
     * as a hit and kills itself, sometimes taking the wrapping shell/SSH
     * channel down with it before it can send back an exit status - this is
     * exactly what produced the "exit -1" failures). The bracket is a
     * single-char regex class, so it still matches the real, unbracketed
     * "agent.py" in the target process's command line, but never matches
     * literally against pkill's own argv (which contains the brackets as
     * plain characters, not a class).
     *
     * Output is also redirected to /dev/null: harmless here since our own
     * exec() already captures stdout/stderr into in-memory buffers rather
     * than a terminal, but it keeps this command's shape self-documenting
     * as "fully silent, never blocks, never fails the request".
     */
    private String buildStopCommand(String remoteDir) {
        return "pkill -f '" + remoteDir + "/[a]gent.py' >/dev/null 2>&1 || true";
    }

    /**
     * Wipes any previous installation under remoteDir (old agent.py, config,
     * log) so every "Sunucu Ekle" run is a clean, from-scratch install
     * rather than an overlay on top of whatever was there before. Safe:
     * remoteDir is server-configured (app.storage.root + "/monitoring"),
     * never user input, but the length guard is a last-resort backstop
     * against ever rm -rf'ing something dangerously short like "/" or "".
     */
    private String buildResetDirectoryCommand(String remoteDir) {
        if (remoteDir == null || remoteDir.length() < 10 || !remoteDir.endsWith("/monitoring")) {
            throw new SshProvisioningException("Guvenli olmayan hedef dizin, temizleme iptal edildi: " + remoteDir);
        }
        return "rm -rf " + remoteDir;
    }

    /**
     * monitoring_user has no login shell, so it can't SSH in or run an
     * interactive session - but root can still launch a command as it via
     * "su -s <shell> <user> -c <cmd>", explicitly overriding the shell used
     * just for this one command. nohup + fully redirected stdio is what lets
     * the process outlive this exec channel once it closes: stdout/stderr go
     * to a file instead of the channel's pipes, and stdin is explicitly
     * pointed at /dev/null (</dev/null) rather than left inherited - without
     * that, the backgrounded agent process can keep holding the channel's
     * stdin pipe open, which can delay the channel from reporting itself
     * closed even though the launching shell command itself returned
     * immediately.
     */
    private String buildStartCommand(String remoteDir) {
        return "su -s /bin/sh " + MONITORING_USER + " -c \"nohup python3 " + remoteDir + "/agent.py > "
                + remoteDir + "/agent.log 2>&1 < /dev/null &\"";
    }

    /**
     * cron runs a user's crontab as that user directly (setuid), never via
     * their login shell, so the nologin shell doesn't block this. Root can
     * manage another user's crontab with "crontab -u". The grep -v strips
     * any @reboot line from a previous provisioning run before appending the
     * current one, so repeated re-provisioning of the same host never
     * accumulates duplicate boot-time launches.
     */
    private String buildCronCommand(String remoteDir) {
        String rebootLine = "@reboot cd " + remoteDir + " && nohup python3 agent.py > "
                + remoteDir + "/agent.log 2>&1 < /dev/null &";
        return "(crontab -u " + MONITORING_USER + " -l 2>/dev/null | grep -v 'agent.py'; echo '" + rebootLine
                + "') | crontab -u " + MONITORING_USER + " -";
    }
}
