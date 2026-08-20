package com.monitoring.poc.repository;

import com.monitoring.poc.entity.CustomMetricLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CustomMetricLogRepository extends JpaRepository<CustomMetricLog, Long> {

    List<CustomMetricLog> findTop50ByGroupIdOrderByCreatedAtDesc(Long groupId);

    /** AIOps kok neden analizi (RCA) icin, sadece izlenen gruplardaki en guncel basarisiz calistirmalar. */
    List<CustomMetricLog> findTop10BySuccessFalseAndGroupIdInOrderByCreatedAtDesc(Collection<Long> groupIds);

    /** AIOps gunluk saglik ozeti (son 24 saat) icin, sadece izlenen gruplardaki basarisiz calistirmalar. */
    List<CustomMetricLog> findByCreatedAtAfterAndSuccessFalseAndGroupIdInOrderByCreatedAtDesc(
            LocalDateTime cutoff, Collection<Long> groupIds);
}
