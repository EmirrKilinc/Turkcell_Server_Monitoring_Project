package com.monitoring.poc.email;

import java.util.List;

/**
 * Interface (not a concrete class) purely so unit tests can mock() it via a
 * JDK dynamic proxy - Mockito's inline mock maker (needed to mock concrete
 * classes) can't redefine bytecode in this JDK25 test environment, the same
 * pre-existing constraint that already excludes ServerServiceTest/
 * HmacAuthFilterTest from the fast unit-test run.
 */
public interface EmailService {

    void sendOtpEmail(String to, String otpCode);

    void sendNewMetricRequestEmail(List<String> adminEmails, String metricKey, String requestedBy);

    void sendMetricApprovedEmail(String to, String metricKey);

    void sendMetricRejectedEmail(String to, String metricKey, String reason);

    void sendConfigDriftEmail(List<String> recipients, String serverName, String fileName);

    void sendProfileChangeRequestEmail(List<String> adminEmails, String username, String changeType);

    void sendProfileChangeApprovedEmail(String to, String changeType);

    void sendProfileChangeRejectedEmail(String to, String changeType, String reason);

    void sendAiOpsAlertEmail(String to, String subject, String summary);

    void sendAiOpsDailySummaryEmail(String to, String summary);
}
