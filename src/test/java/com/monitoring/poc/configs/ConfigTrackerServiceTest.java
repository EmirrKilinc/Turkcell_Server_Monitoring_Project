package com.monitoring.poc.configs;

import com.monitoring.poc.configs.dto.AgentConfigReportRequest;
import com.monitoring.poc.configs.dto.AgentConfigSyncResponse;
import com.monitoring.poc.configs.dto.TrackedConfigFileRequest;
import com.monitoring.poc.configs.dto.TrackedConfigFileResponse;
import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.ConfigFileHistory;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.entity.TrackedConfigFile;
import com.monitoring.poc.enums.ConfigFileStatus;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.repository.ConfigFileHistoryRepository;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.repository.TrackedConfigFileRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigTrackerServiceTest {

    private TrackedConfigFileRepository trackedConfigFileRepository;
    private ConfigFileHistoryRepository configFileHistoryRepository;
    private ServerRepository serverRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private NotificationService notificationService;
    private ConfigTrackerService service;

    private Server server;

    @BeforeEach
    void setUp() {
        trackedConfigFileRepository = mock(TrackedConfigFileRepository.class);
        configFileHistoryRepository = mock(ConfigFileHistoryRepository.class);
        serverRepository = mock(ServerRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        notificationService = mock(NotificationService.class);
        service = new ConfigTrackerService(trackedConfigFileRepository, configFileHistoryRepository,
                serverRepository, userRepository, emailService, notificationService);

        server = new Server("cfg-host-01", "10.0.0.9", "monitoring_user", "enc", ServerStatus.ACTIVE, null);
        server.setId(1L);
    }

    private TrackedConfigFileRequest request() {
        TrackedConfigFileRequest req = new TrackedConfigFileRequest();
        req.setServerId(1L);
        req.setFilePath("/data01/monitoring/agent_config.json");
        req.setFileLabel("Agent Config");
        req.setCheckIntervalSeconds(60);
        return req;
    }

    @Test
    void rejectsCreateAsConflictWhenPathAlreadyTrackedOnServer() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(trackedConfigFileRepository.existsByServer_IdAndFilePath(1L, request().getFilePath())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(), "op1")).isInstanceOf(ApiException.class);
    }

    @Test
    void createSucceedsWithPendingStatus() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(trackedConfigFileRepository.existsByServer_IdAndFilePath(1L, request().getFilePath())).thenReturn(false);

        TrackedConfigFileResponse response = service.create(request(), "op1");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getCreatedBy()).isEqualTo("op1");
    }

    @Test
    void buildSyncResponseOmitsNotYetDueFileAndIncludesDueOnes() {
        TrackedConfigFile due = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        due.setId(1L);
        // lastCheckedAt left null -> always due

        TrackedConfigFile notDue = new TrackedConfigFile(server, "/b.conf", "B", 3600, "op1");
        notDue.setId(2L);
        notDue.setLastCheckedAt(LocalDateTime.now());

        when(trackedConfigFileRepository.findByServer_Id(1L)).thenReturn(List.of(due, notDue));

        AgentConfigSyncResponse response = service.buildSyncResponse(1L);

        assertThat(response.getDueFiles()).hasSize(1);
        assertThat(response.getDueFiles().get(0).getTrackedFileId()).isEqualTo(1L);
    }

    @Test
    void recordReportFirstCaptureCreatesV1BaselineAndSetsInSync() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(configFileHistoryRepository.findByTrackedFileIdOrderByVersionNumberDesc(1L)).thenReturn(List.of());

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setHash("hash-v1");
        report.setContent("setting=1\n");

        service.recordReport(1L, report);

        ArgumentCaptor<ConfigFileHistory> historyCaptor = ArgumentCaptor.forClass(ConfigFileHistory.class);
        verify(configFileHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(file.getStatus()).isEqualTo(ConfigFileStatus.IN_SYNC);
        assertThat(file.getCurrentHash()).isEqualTo("hash-v1");
    }

    @Test
    void recordReportChangedHashCreatesNewVersionAndMarksDrift() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setCurrentHash("hash-v1");
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        ConfigFileHistory v1 = new ConfigFileHistory(1L, 1, "setting=1\n", "hash-v1", "Baseline (v1)");
        when(configFileHistoryRepository.findByTrackedFileIdOrderByVersionNumberDesc(1L)).thenReturn(List.of(v1));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setHash("hash-v2");
        report.setContent("setting=2\n");

        service.recordReport(1L, report);

        ArgumentCaptor<ConfigFileHistory> historyCaptor = ArgumentCaptor.forClass(ConfigFileHistory.class);
        verify(configFileHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(historyCaptor.getValue().getDiffSummary()).isEqualTo("+1 -1");
        assertThat(file.getStatus()).isEqualTo(ConfigFileStatus.DRIFT_DETECTED);
        assertThat(file.getCurrentHash()).isEqualTo("hash-v2");
        verify(emailService).sendConfigDriftEmail(any(), eq("cfg-host-01"), eq("/a.conf"));
    }

    @Test
    void recordReportUnchangedSetsInSyncWithoutNewHistoryRow() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setCurrentHash("hash-v1");
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setUnchanged(true);

        service.recordReport(1L, report);

        verify(configFileHistoryRepository, never()).save(any());
        assertThat(file.getStatus()).isEqualTo(ConfigFileStatus.IN_SYNC);
    }

    @Test
    void recordReportFileNotFoundSetsStatusWithoutNewHistoryRow() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setError("FILE_NOT_FOUND");

        service.recordReport(1L, report);

        verify(configFileHistoryRepository, never()).save(any());
        assertThat(file.getStatus()).isEqualTo(ConfigFileStatus.FILE_NOT_FOUND);
    }

    @Test
    void recordReportIgnoresReportForAFileBelongingToAnotherServer() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setUnchanged(true);

        service.recordReport(999L, report);

        verify(trackedConfigFileRepository, never()).save(any());
    }

    @Test
    void getDiffReturnsNotFoundForMissingVersion() {
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(
                new TrackedConfigFile(server, "/a.conf", "A", 60, "op1")));
        when(configFileHistoryRepository.findByTrackedFileIdAndVersionNumber(1L, 1))
                .thenReturn(Optional.of(new ConfigFileHistory(1L, 1, "a", "h1", "Baseline (v1)")));
        when(configFileHistoryRepository.findByTrackedFileIdAndVersionNumber(1L, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDiff(1L, 1, 5)).isInstanceOf(ApiException.class);
    }

    @Test
    void recordReportUnchangedAfterDriftDetectedKeepsDriftDetected() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setCurrentHash("hash-v2");
        file.setStatus(ConfigFileStatus.DRIFT_DETECTED);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setUnchanged(true);

        service.recordReport(1L, report);

        verify(configFileHistoryRepository, never()).save(any());
        assertThat(file.getStatus()).isEqualTo(ConfigFileStatus.DRIFT_DETECTED);
    }

    @Test
    void recordReportClearsForceCheckRequestedRegardlessOfOutcome() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setForceCheckRequested(true);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        AgentConfigReportRequest report = new AgentConfigReportRequest();
        report.setTrackedFileId(1L);
        report.setError("FILE_NOT_FOUND");

        service.recordReport(1L, report);

        assertThat(file.getForceCheckRequested()).isFalse();
    }

    @Test
    void checkNowSetsForceCheckRequestedAndMakesFileDueImmediately() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 3600, "op1");
        file.setId(1L);
        file.setLastCheckedAt(LocalDateTime.now());
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        TrackedConfigFileResponse response = service.checkNow(1L);
        assertThat(response.getForceCheckRequested()).isTrue();

        when(trackedConfigFileRepository.findByServer_Id(1L)).thenReturn(List.of(file));
        AgentConfigSyncResponse syncResponse = service.buildSyncResponse(1L);
        assertThat(syncResponse.getDueFiles()).hasSize(1);
    }

    @Test
    void acceptBaselineTransitionsDriftDetectedToInSync() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setStatus(ConfigFileStatus.DRIFT_DETECTED);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        TrackedConfigFileResponse response = service.acceptBaseline(1L, "admin1");

        assertThat(response.getStatus()).isEqualTo("IN_SYNC");
        assertThat(response.getBaselineAcceptedBy()).isEqualTo("admin1");
        assertThat(response.getBaselineAcceptedAt()).isNotNull();
    }

    @Test
    void acceptBaselineOnNonDriftedFileIsConflict() {
        TrackedConfigFile file = new TrackedConfigFile(server, "/a.conf", "A", 60, "op1");
        file.setId(1L);
        file.setStatus(ConfigFileStatus.IN_SYNC);
        when(trackedConfigFileRepository.findById(1L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.acceptBaseline(1L, "admin1")).isInstanceOf(ApiException.class);
    }
}
