package com.monitoring.poc.notifications;

import com.monitoring.poc.entity.Notification;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.dto.NotificationResponse;
import com.monitoring.poc.repository.NotificationRepository;
import com.monitoring.poc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void notify(String recipientUsername, NotificationType type, String title, String message, String link) {
        notificationRepository.save(new Notification(recipientUsername, type, title, message, link));
    }

    @Override
    public void notifyAdmins(NotificationType type, String title, String message, String link) {
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notify(admin.getUsername(), type, title, message, link);
        }
    }

    @Override
    public List<NotificationResponse> list(String username) {
        return notificationRepository.findTop30ByRecipientUsernameOrderByCreatedAtDesc(username).stream()
                .map(NotificationResponse::new)
                .toList();
    }

    @Override
    public long unreadCount(String username) {
        return notificationRepository.countByRecipientUsernameAndIsReadFalse(username);
    }

    @Override
    @Transactional
    public void markRead(Long id, String username) {
        Notification notification = notificationRepository.findByIdAndRecipientUsername(id, username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bildirim bulunamadi"));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(String username) {
        List<Notification> unread = notificationRepository.findByRecipientUsernameAndIsReadFalse(username);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }
}
