package com.monitoring.poc.notifications;

import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.notifications.dto.NotificationResponse;

import java.util.List;

/**
 * Interface (not a concrete class) purely so unit tests can mock() it via a
 * JDK dynamic proxy - Mockito's inline mock maker (needed to mock concrete
 * classes) can't redefine bytecode in this JDK25 test environment, the same
 * reason EmailService is an interface too.
 */
public interface NotificationService {

    void notify(String recipientUsername, NotificationType type, String title, String message, String link);

    void notifyAdmins(NotificationType type, String title, String message, String link);

    List<NotificationResponse> list(String username);

    long unreadCount(String username);

    void markRead(Long id, String username);

    void markAllRead(String username);
}
