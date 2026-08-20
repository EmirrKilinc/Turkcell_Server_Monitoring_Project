package com.monitoring.poc.metrics;

import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.MetricValueType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.MetricDefinitionRequest;
import com.monitoring.poc.metrics.dto.MetricDefinitionResponse;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricDefinitionServiceTest {

    private MetricDefinitionRepository metricDefinitionRepository;
    private MetricGroupItemRepository metricGroupItemRepository;
    private UserRepository userRepository;
    private EmailService emailService;
    private NotificationService notificationService;
    private MetricDefinitionService service;

    @BeforeEach
    void setUp() {
        metricDefinitionRepository = mock(MetricDefinitionRepository.class);
        metricGroupItemRepository = mock(MetricGroupItemRepository.class);
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        notificationService = mock(NotificationService.class);
        service = new MetricDefinitionService(metricDefinitionRepository, metricGroupItemRepository,
                userRepository, emailService, notificationService);
    }

    private MetricDefinitionRequest request() {
        MetricDefinitionRequest req = new MetricDefinitionRequest();
        req.setName("Sistem Yuk Ortalamasi");
        req.setMetricKey("system_loadavg");
        req.setCategory("SYSTEM");
        req.setCommand("cat /proc/loadavg");
        req.setTimeoutSeconds(3);
        req.setValueType(MetricValueType.RAW);
        return req;
    }

    private MetricDefinition entity() {
        return new MetricDefinition("X", "x_key", "SYSTEM", "cat /proc/loadavg", 3, MetricValueType.RAW, null, null);
    }

    @Test
    void createsANewDefinitionWhenKeyIsUnused() {
        when(metricDefinitionRepository.existsByMetricKey("system_loadavg")).thenReturn(false);

        MetricDefinitionResponse response = service.create(request(), true, "admin1");

        assertThat(response.getMetricKey()).isEqualTo("system_loadavg");
        assertThat(response.getCommand()).isEqualTo("cat /proc/loadavg");
        assertThat(response.getValueType()).isEqualTo("RAW");
    }

    @Test
    void rejectsCreateAsConflictWhenMetricKeyAlreadyExists() {
        when(metricDefinitionRepository.existsByMetricKey("system_loadavg")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(), true, "admin1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("system_loadavg");
    }

    @Test
    void rejectsDeleteWhenDefinitionIsReferencedByAGroupItem() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(metricGroupItemRepository.existsByMetricDefinition_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ApiException.class);
        verify(metricDefinitionRepository, never()).delete(existing);
    }

    @Test
    void allowsDeleteWhenDefinitionIsNotReferenced() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(metricGroupItemRepository.existsByMetricDefinition_Id(1L)).thenReturn(false);

        service.delete(1L);

        verify(metricDefinitionRepository).delete(existing);
    }

    @Test
    void gettingAMissingDefinitionThrowsNotFound() {
        when(metricDefinitionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ApiException.class);
    }

    @Test
    void updateOverwritesStructuredFields() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(metricDefinitionRepository.existsByMetricKey("system_loadavg")).thenReturn(false);

        MetricDefinitionResponse response = service.update(1L, request(), true);

        assertThat(response.getCommand()).isEqualTo("cat /proc/loadavg");
        assertThat(response.getTimeoutSeconds()).isEqualTo(3);
        assertThat(response.getValueType()).isEqualTo("RAW");
    }

    // ---------- Approval workflow ----------

    @Test
    void operatorCreateLandsAsPendingApproval() {
        when(metricDefinitionRepository.existsByMetricKey(anyString())).thenReturn(false);
        User admin = new User("admin1", "admin1@example.com", "hash", Role.ADMIN, UserStatus.APPROVED);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        MetricDefinitionResponse response = service.create(request(), false, "op1");

        assertThat(response.getApprovalStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getCreatedBy()).isEqualTo("op1");
        assertThat(response.getApprovedBy()).isNull();
        verify(emailService).sendNewMetricRequestEmail(List.of("admin1@example.com"), "system_loadavg", "op1");
    }

    @Test
    void adminCreateIsAutoApproved() {
        when(metricDefinitionRepository.existsByMetricKey(anyString())).thenReturn(false);

        MetricDefinitionResponse response = service.create(request(), true, "admin1");

        assertThat(response.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(response.getApprovedBy()).isEqualTo("admin1");
        assertThat(response.getApprovedAt()).isNotNull();
    }

    @Test
    void approvingAPendingDefinitionMarksItApproved() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        existing.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        existing.setCreatedBy("op1");
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        User operator = new User("op1", "op1@example.com", "hash", Role.OPERATOR, UserStatus.APPROVED);
        when(userRepository.findByUsername("op1")).thenReturn(Optional.of(operator));

        MetricDefinitionResponse response = service.approve(1L, "admin1");

        assertThat(response.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(response.getApprovedBy()).isEqualTo("admin1");
        verify(emailService).sendMetricApprovedEmail("op1@example.com", existing.getMetricKey());
    }

    @Test
    void approvingAnAlreadyDecidedDefinitionIsConflict() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        existing.setApprovalStatus(ApprovalStatus.APPROVED);
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.approve(1L, "admin1")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectingWithBlankReasonIsBadRequest() {
        assertThatThrownBy(() -> service.reject(1L, "admin1", "  ")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectingAPendingDefinitionSetsReasonAndReviewer() {
        MetricDefinition existing = entity();
        existing.setId(1L);
        existing.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        existing.setCreatedBy("op1");
        when(metricDefinitionRepository.findById(1L)).thenReturn(Optional.of(existing));
        User operator = new User("op1", "op1@example.com", "hash", Role.OPERATOR, UserStatus.APPROVED);
        when(userRepository.findByUsername("op1")).thenReturn(Optional.of(operator));

        MetricDefinitionResponse response = service.reject(1L, "admin1", "Guvenli degil");

        assertThat(response.getApprovalStatus()).isEqualTo("REJECTED");
        assertThat(response.getApprovedBy()).isEqualTo("admin1");
        assertThat(response.getRejectionReason()).isEqualTo("Guvenli degil");
        verify(emailService).sendMetricRejectedEmail("op1@example.com", existing.getMetricKey(), "Guvenli degil");
    }
}
