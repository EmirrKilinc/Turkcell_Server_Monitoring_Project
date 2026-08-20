package com.monitoring.poc.notifications;

import com.monitoring.poc.entity.Notification;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.dto.NotificationResponse;
import com.monitoring.poc.repository.NotificationRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new NotificationServiceImpl(notificationRepository, userRepository);
    }

    @Test
    void notifySavesOneNotificationForTheRecipient() {
        service.notify("op1", NotificationType.METRIC_APPROVED, "Title", "Message", "metrics.html");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUsername()).isEqualTo("op1");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.METRIC_APPROVED);
        assertThat(captor.getValue().getIsRead()).isFalse();
    }

    @Test
    void notifyAdminsSavesOneNotificationPerAdmin() {
        User admin1 = new User("admin1", "a1@example.com", "hash", Role.ADMIN, UserStatus.APPROVED);
        User admin2 = new User("admin2", "a2@example.com", "hash", Role.ADMIN, UserStatus.APPROVED);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin1, admin2));

        service.notifyAdmins(NotificationType.METRIC_REQUEST, "Title", "Message", "metrics.html");

        verify(notificationRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markReadThrowsWhenNotificationDoesNotBelongToTheCaller() {
        when(notificationRepository.findByIdAndRecipientUsername(1L, "op1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(1L, "op1")).isInstanceOf(ApiException.class);
    }

    @Test
    void markReadFlipsIsReadToTrue() {
        Notification notification = new Notification("op1", NotificationType.METRIC_APPROVED, "T", "M", null);
        when(notificationRepository.findByIdAndRecipientUsername(1L, "op1")).thenReturn(Optional.of(notification));

        service.markRead(1L, "op1");

        assertThat(notification.getIsRead()).isTrue();
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(notificationRepository.countByRecipientUsernameAndIsReadFalse("op1")).thenReturn(3L);

        assertThat(service.unreadCount("op1")).isEqualTo(3L);
    }

    @Test
    void listMapsToResponseDtos() {
        Notification notification = new Notification("op1", NotificationType.CONFIG_DRIFT, "T", "M", "configs.html");
        when(notificationRepository.findTop30ByRecipientUsernameOrderByCreatedAtDesc("op1"))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = service.list("op1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("CONFIG_DRIFT");
    }
}
