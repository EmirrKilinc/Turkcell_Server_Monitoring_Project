package com.monitoring.poc.repository;

import com.monitoring.poc.entity.AiOpsAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiOpsAnomalyRepository extends JpaRepository<AiOpsAnomaly, Long> {

    List<AiOpsAnomaly> findTop10ByOrderByCreatedAtDesc();
}
