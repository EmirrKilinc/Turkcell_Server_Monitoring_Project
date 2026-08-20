package com.monitoring.poc.configs;

import com.monitoring.poc.configs.dto.AgentConfigReportRequest;
import com.monitoring.poc.configs.dto.AgentConfigSyncItemDto;
import com.monitoring.poc.configs.dto.AgentConfigSyncResponse;
import com.monitoring.poc.configs.dto.ConfigFileDiffResponse;
import com.monitoring.poc.configs.dto.ConfigFileHistorySummaryResponse;
import com.monitoring.poc.configs.dto.DiffLineDto;
import com.monitoring.poc.configs.dto.TrackedConfigFileRequest;
import com.monitoring.poc.configs.dto.TrackedConfigFileResponse;
import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.ConfigFileHistory;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.entity.TrackedConfigFile;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.ConfigFileStatus;
import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.repository.ConfigFileHistoryRepository;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.repository.TrackedConfigFileRepository;
import com.monitoring.poc.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mirrors AgentSyncService's shape: the server is the single source of
 * truth, the agent is a dumb reporter, and "due" is recomputed fresh from
 * the DB on every sync call rather than pushed.
 */
@Service
public class ConfigTrackerService {

    private static final Logger log = LoggerFactory.getLogger(ConfigTrackerService.class);

    private final TrackedConfigFileRepository trackedConfigFileRepository;
    private final ConfigFileHistoryRepository configFileHistoryRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public ConfigTrackerService(TrackedConfigFileRepository trackedConfigFileRepository,
                                 ConfigFileHistoryRepository configFileHistoryRepository,
                                 ServerRepository serverRepository,
                                 UserRepository userRepository,
                                 EmailService emailService,
                                 NotificationService notificationService) {
        this.trackedConfigFileRepository = trackedConfigFileRepository;
        this.configFileHistoryRepository = configFileHistoryRepository;
        this.serverRepository = serverRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public List<TrackedConfigFileResponse> listByServer(Long serverId) {
        return trackedConfigFileRepository.findByServer_Id(serverId).stream()
                .map(TrackedConfigFileResponse::new)
                .toList();
    }

    @Transactional
    public TrackedConfigFileResponse create(TrackedConfigFileRequest request, String username) {
        Server server = serverRepository.findById(request.getServerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sunucu bulunamadi"));

        if (trackedConfigFileRepository.existsByServer_IdAndFilePath(request.getServerId(), request.getFilePath())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu sunucuda (" + server.getHostname() + ") '" + request.getFilePath() + "' zaten takip ediliyor");
        }

        TrackedConfigFile file = new TrackedConfigFile(server, request.getFilePath(), request.getFileLabel(),
                request.getCheckIntervalSeconds(), username);
        trackedConfigFileRepository.save(file);
        return new TrackedConfigFileResponse(file);
    }

    @Transactional
    public void delete(Long id) {
        TrackedConfigFile file = findOrThrow(id);
        trackedConfigFileRepository.delete(file);
    }

    public List<ConfigFileHistorySummaryResponse> getHistory(Long trackedFileId) {
        findOrThrow(trackedFileId);
        return configFileHistoryRepository.findByTrackedFileIdOrderByVersionNumberDesc(trackedFileId).stream()
                .map(ConfigFileHistorySummaryResponse::new)
                .toList();
    }

    public ConfigFileDiffResponse getDiff(Long trackedFileId, Integer v1, Integer v2) {
        findOrThrow(trackedFileId);
        ConfigFileHistory version1 = findVersionOrThrow(trackedFileId, v1);
        ConfigFileHistory version2 = findVersionOrThrow(trackedFileId, v2);

        List<DiffLineDto> lines = DiffEngine.diff(version1.getFileContent(), version2.getFileContent()).stream()
                .map(DiffLineDto::new)
                .toList();
        return new ConfigFileDiffResponse(v1, v2, lines);
    }

    @Transactional(readOnly = true)
    public AgentConfigSyncResponse buildSyncResponse(Long serverId) {
        LocalDateTime now = LocalDateTime.now();
        List<AgentConfigSyncItemDto> dueFiles = trackedConfigFileRepository.findByServer_Id(serverId).stream()
                .filter(file -> isDue(file, now))
                .map(file -> new AgentConfigSyncItemDto(file.getId(), file.getFilePath(), file.getCurrentHash()))
                .toList();
        return new AgentConfigSyncResponse(dueFiles);
    }

    @Transactional
    public void recordReport(Long serverId, AgentConfigReportRequest report) {
        Optional<TrackedConfigFile> fileOpt = trackedConfigFileRepository.findById(report.getTrackedFileId());
        if (fileOpt.isEmpty() || !fileOpt.get().getServer().getId().equals(serverId)) {
            log.warn("Agent bilinmeyen veya baska bir sunucuya ait yapilandirma dosyasi icin rapor gonderdi: trackedFileId={}",
                    report.getTrackedFileId());
            return;
        }

        TrackedConfigFile file = fileOpt.get();
        file.setLastCheckedAt(LocalDateTime.now());
        // A forced check is consumed as soon as it's processed, regardless
        // of which outcome branch below runs.
        file.setForceCheckRequested(false);

        if (report.getError() != null) {
            file.setStatus(ConfigFileStatus.valueOf(report.getError()));
            trackedConfigFileRepository.save(file);
            return;
        }

        if (Boolean.TRUE.equals(report.getUnchanged())) {
            // Drift stays flagged until a human calls acceptBaseline() -
            // an "unchanged since last check" report must never silently
            // clear an unacknowledged DRIFT_DETECTED back to IN_SYNC.
            if (file.getStatus() != ConfigFileStatus.DRIFT_DETECTED) {
                file.setStatus(ConfigFileStatus.IN_SYNC);
            }
            trackedConfigFileRepository.save(file);
            return;
        }

        // New or changed content - capture a new version. A pre-existing
        // history row means this is a real drift from a known baseline;
        // no prior row means this is the very first capture (v1 baseline).
        List<ConfigFileHistory> existingVersions =
                configFileHistoryRepository.findByTrackedFileIdOrderByVersionNumberDesc(file.getId());
        String previousContent = existingVersions.isEmpty() ? "" : existingVersions.get(0).getFileContent();
        int nextVersion = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersionNumber() + 1;

        String diffSummary = DiffEngine.summarize(DiffEngine.diff(previousContent, report.getContent()));
        ConfigFileHistory history = new ConfigFileHistory(file.getId(), nextVersion, report.getContent(),
                report.getHash(), diffSummary);
        configFileHistoryRepository.save(history);

        file.setCurrentHash(report.getHash());
        if (existingVersions.isEmpty()) {
            file.setStatus(ConfigFileStatus.IN_SYNC);
        } else {
            file.setStatus(ConfigFileStatus.DRIFT_DETECTED);
            notifyDrift(file);
        }
        trackedConfigFileRepository.save(file);
    }

    private void notifyDrift(TrackedConfigFile file) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        List<String> recipients = new ArrayList<>(admins.stream().map(User::getEmail).toList());
        userRepository.findByUsername(file.getCreatedBy()).ifPresent(u -> recipients.add(u.getEmail()));
        emailService.sendConfigDriftEmail(recipients.stream().distinct().toList(),
                file.getServer().getHostname(), file.getFilePath());

        String title = "Yapilandirma Sapmasi Tespit Edildi";
        String message = file.getServer().getHostname() + " - \"" + file.getFilePath() + "\" dosyasinda sapma tespit edildi.";
        List<String> notifyUsernames = new ArrayList<>(admins.stream().map(User::getUsername).toList());
        if (file.getCreatedBy() != null) {
            notifyUsernames.add(file.getCreatedBy());
        }
        notifyUsernames.stream().distinct()
                .forEach(u -> notificationService.notify(u, NotificationType.CONFIG_DRIFT, title, message, "configs.html"));
    }

    @Transactional
    public TrackedConfigFileResponse checkNow(Long id) {
        TrackedConfigFile file = findOrThrow(id);
        file.setForceCheckRequested(true);
        trackedConfigFileRepository.save(file);
        return new TrackedConfigFileResponse(file);
    }

    @Transactional
    public TrackedConfigFileResponse acceptBaseline(Long id, String username) {
        TrackedConfigFile file = findOrThrow(id);
        if (file.getStatus() != ConfigFileStatus.DRIFT_DETECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu dosyada suanda onaylanacak bir sapma yok");
        }
        file.setStatus(ConfigFileStatus.IN_SYNC);
        file.setBaselineAcceptedBy(username);
        file.setBaselineAcceptedAt(LocalDateTime.now());
        trackedConfigFileRepository.save(file);
        return new TrackedConfigFileResponse(file);
    }

    private boolean isDue(TrackedConfigFile file, LocalDateTime now) {
        if (Boolean.TRUE.equals(file.getForceCheckRequested()) || file.getLastCheckedAt() == null) {
            return true;
        }
        LocalDateTime nextDue = file.getLastCheckedAt().plusSeconds(file.getCheckIntervalSeconds());
        return !nextDue.isAfter(now);
    }

    private TrackedConfigFile findOrThrow(Long id) {
        return trackedConfigFileRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Takip edilen dosya bulunamadi"));
    }

    private ConfigFileHistory findVersionOrThrow(Long trackedFileId, Integer version) {
        return configFileHistoryRepository.findByTrackedFileIdAndVersionNumber(trackedFileId, version)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Versiyon bulunamadi: v" + version));
    }
}
