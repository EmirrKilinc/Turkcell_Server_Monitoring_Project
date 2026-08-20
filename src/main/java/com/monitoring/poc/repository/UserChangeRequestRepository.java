package com.monitoring.poc.repository;

import com.monitoring.poc.entity.UserChangeRequest;
import com.monitoring.poc.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserChangeRequestRepository extends JpaRepository<UserChangeRequest, Long> {

    boolean existsByUser_IdAndStatus(Long userId, ApprovalStatus status);

    List<UserChangeRequest> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<UserChangeRequest> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
}
