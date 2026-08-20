package com.monitoring.poc.repository;

import com.monitoring.poc.entity.UserOtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOtpVerificationRepository extends JpaRepository<UserOtpVerification, Long> {

    Optional<UserOtpVerification> findByTempTokenAndIsUsedFalse(String tempToken);
}
