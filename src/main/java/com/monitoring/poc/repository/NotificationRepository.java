package com.monitoring.poc.repository;

import com.monitoring.poc.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername);

    long countByRecipientUsernameAndIsReadFalse(String recipientUsername);

    List<Notification> findByRecipientUsernameAndIsReadFalse(String recipientUsername);

    Optional<Notification> findByIdAndRecipientUsername(Long id, String recipientUsername);
}
