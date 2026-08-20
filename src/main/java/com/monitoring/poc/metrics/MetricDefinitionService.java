package com.monitoring.poc.metrics;

import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.MetricDefinitionRequest;
import com.monitoring.poc.metrics.dto.MetricDefinitionResponse;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricDefinitionService {

    private final MetricDefinitionRepository metricDefinitionRepository;
    private final MetricGroupItemRepository metricGroupItemRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public MetricDefinitionService(MetricDefinitionRepository metricDefinitionRepository,
                                    MetricGroupItemRepository metricGroupItemRepository,
                                    UserRepository userRepository,
                                    EmailService emailService,
                                    NotificationService notificationService) {
        this.metricDefinitionRepository = metricDefinitionRepository;
        this.metricGroupItemRepository = metricGroupItemRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public List<MetricDefinitionResponse> listAll() {
        return metricDefinitionRepository.findAll().stream()
                .map(MetricDefinitionResponse::new)
                .toList();
    }

    public MetricDefinitionResponse getById(Long id) {
        return new MetricDefinitionResponse(findOrThrow(id));
    }

    @Transactional
    public MetricDefinitionResponse create(MetricDefinitionRequest request, boolean isAdmin, String username) {
        if (metricDefinitionRepository.existsByMetricKey(request.getMetricKey())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu metric_key (" + request.getMetricKey() + ") zaten kullaniliyor");
        }

        MetricDefinition definition = new MetricDefinition(
                request.getName(),
                request.getMetricKey(),
                request.getCategory(),
                request.getCommand(),
                request.getTimeoutSeconds(),
                request.getValueType(),
                request.getExtractPattern(),
                request.getDescription()
        );
        definition.setCreatedBy(username);
        if (isAdmin) {
            definition.setApprovalStatus(ApprovalStatus.APPROVED);
            definition.setApprovedBy(username);
            definition.setApprovedAt(LocalDateTime.now());
        } else {
            definition.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        }
        metricDefinitionRepository.save(definition);

        if (!isAdmin) {
            List<String> adminEmails = userRepository.findByRole(Role.ADMIN).stream()
                    .map(User::getEmail)
                    .toList();
            emailService.sendNewMetricRequestEmail(adminEmails, definition.getMetricKey(), username);
            notificationService.notifyAdmins(NotificationType.METRIC_REQUEST,
                    "Yeni Metrik Onay Talebi",
                    username + " kullanicisi \"" + definition.getMetricKey() + "\" metrigini talep etti.",
                    "metrics.html");
        }

        return new MetricDefinitionResponse(definition);
    }

    @Transactional
    public MetricDefinitionResponse update(Long id, MetricDefinitionRequest request, boolean isAdmin) {
        MetricDefinition definition = findOrThrow(id);

        if (!definition.getMetricKey().equals(request.getMetricKey())
                && metricDefinitionRepository.existsByMetricKey(request.getMetricKey())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu metric_key (" + request.getMetricKey() + ") zaten kullaniliyor");
        }

        definition.setName(request.getName());
        definition.setMetricKey(request.getMetricKey());
        definition.setCategory(request.getCategory());
        definition.setCommand(request.getCommand());
        definition.setTimeoutSeconds(request.getTimeoutSeconds());
        definition.setValueType(request.getValueType());
        definition.setExtractPattern(request.getExtractPattern());
        definition.setDescription(request.getDescription());
        definition.setUpdatedAt(LocalDateTime.now());
        metricDefinitionRepository.save(definition);
        return new MetricDefinitionResponse(definition);
    }

    @Transactional
    public MetricDefinitionResponse approve(Long id, String adminUsername) {
        MetricDefinition definition = findOrThrow(id);
        requirePending(definition);
        definition.setApprovalStatus(ApprovalStatus.APPROVED);
        definition.setApprovedBy(adminUsername);
        definition.setApprovedAt(LocalDateTime.now());
        definition.setRejectionReason(null);
        metricDefinitionRepository.save(definition);

        userRepository.findByUsername(definition.getCreatedBy())
                .ifPresent(u -> emailService.sendMetricApprovedEmail(u.getEmail(), definition.getMetricKey()));
        if (definition.getCreatedBy() != null) {
            notificationService.notify(definition.getCreatedBy(), NotificationType.METRIC_APPROVED,
                    "Metrik Talebiniz Onaylandi",
                    "\"" + definition.getMetricKey() + "\" anahtarli metrik talebiniz onaylandi.",
                    "metrics.html");
        }

        return new MetricDefinitionResponse(definition);
    }

    @Transactional
    public MetricDefinitionResponse reject(Long id, String adminUsername, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reddetme gerekcesi (rejectionReason) zorunludur");
        }
        MetricDefinition definition = findOrThrow(id);
        requirePending(definition);
        definition.setApprovalStatus(ApprovalStatus.REJECTED);
        definition.setApprovedBy(adminUsername);
        definition.setRejectionReason(reason);
        metricDefinitionRepository.save(definition);

        userRepository.findByUsername(definition.getCreatedBy())
                .ifPresent(u -> emailService.sendMetricRejectedEmail(u.getEmail(), definition.getMetricKey(), reason));
        if (definition.getCreatedBy() != null) {
            notificationService.notify(definition.getCreatedBy(), NotificationType.METRIC_REJECTED,
                    "Metrik Talebiniz Reddedildi",
                    "\"" + definition.getMetricKey() + "\" anahtarli metrik talebiniz reddedildi. Gerekce: " + reason,
                    "metrics.html");
        }

        return new MetricDefinitionResponse(definition);
    }

    private void requirePending(MetricDefinition definition) {
        if (definition.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu metrik tanimi zaten degerlendirilmis");
        }
    }

    @Transactional
    public void delete(Long id) {
        MetricDefinition definition = findOrThrow(id);
        if (metricGroupItemRepository.existsByMetricDefinition_Id(id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu metrik tanimi bir veya daha fazla grupta kullaniliyor, once gruplardan cikarin");
        }
        metricDefinitionRepository.delete(definition);
    }

    private MetricDefinition findOrThrow(Long id) {
        return metricDefinitionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Metrik tanimi bulunamadi"));
    }
}
