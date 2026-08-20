package com.monitoring.poc.repository;

import com.monitoring.poc.entity.MetricFetchRequest;
import com.monitoring.poc.enums.FetchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricFetchRequestRepository extends JpaRepository<MetricFetchRequest, Long> {

    List<MetricFetchRequest> findByServerIdAndStatus(Long serverId, FetchRequestStatus status);

    List<MetricFetchRequest> findByStatusAndRequestedAtBefore(FetchRequestStatus status, LocalDateTime cutoff);
}
