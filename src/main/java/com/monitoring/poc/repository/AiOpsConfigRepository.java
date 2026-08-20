package com.monitoring.poc.repository;

import com.monitoring.poc.entity.AiOpsConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiOpsConfigRepository extends JpaRepository<AiOpsConfig, Long> {
}
