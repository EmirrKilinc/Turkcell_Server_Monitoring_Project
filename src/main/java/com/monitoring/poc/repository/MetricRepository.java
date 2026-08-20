package com.monitoring.poc.repository;

import com.monitoring.poc.entity.MetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricRepository extends JpaRepository<MetricEntity, Long> {

    @Query("SELECT DISTINCT m.hostname FROM MetricEntity m ORDER BY m.hostname ASC")
    List<String> findDistinctHostnames();

    List<MetricEntity> findTop10ByHostnameOrderByCreatedAtDesc(String hostname);

    MetricEntity findFirstByHostnameOrderByCreatedAtDesc(String hostname);

    /** AIOps gunluk saglik ozeti (son 24 saat) icin kullanilir. */
    List<MetricEntity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime cutoff);
}
