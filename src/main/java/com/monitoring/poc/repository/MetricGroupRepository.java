package com.monitoring.poc.repository;

import com.monitoring.poc.entity.MetricGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricGroupRepository extends JpaRepository<MetricGroup, Long> {

    List<MetricGroup> findByServer_Id(Long serverId);

    boolean existsByServer_IdAndName(Long serverId, String name);
}
