package com.monitoring.poc.repository;

import com.monitoring.poc.entity.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Long> {

    Optional<MetricDefinition> findByMetricKey(String metricKey);

    boolean existsByMetricKey(String metricKey);
}
